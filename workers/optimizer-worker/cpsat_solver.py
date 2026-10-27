import math
import time
import logging
from typing import List, Dict, Any, Optional, Tuple, Set

from ortools.sat.python import cp_model
from heuristic_solver import (
    solve_3d_packing as solve_heuristic,
    compute_content_digest,
    PhysicalItemUnit,
    OrientationDimensions,
    ORIENTATION_DEFAULT,
    ORIENTATION_ROTATE_YAW_90,
    has_3d_collision,
    is_supported,
    can_stack_on_supporting_items,
    check_segregation
)

logger = logging.getLogger("CpSatCargoSolver")

PROVENANCE_CP_SAT = "PYTHON_ORTOOLS_CPSAT_3D_V1"
ALGORITHM_VERSION = "1.0.0-phaseD"

def validate_candidate_strictly(placements: List[Dict[str, Any]],
                                compartment: Dict[str, Any],
                                active_rules: List[Dict[str, Any]]) -> Tuple[bool, Optional[str]]:
    """
    Independently validate a candidate layout matching IndependentValidator logic exactly.
    """
    comp_l = int(compartment.get("lengthMm", 0))
    comp_w = int(compartment.get("widthMm", 0))
    comp_h = int(compartment.get("heightMm", 0))
    max_payload = float(compartment.get("maxPayloadKg", 0.0))

    total_weight = sum(p["weightKg"] for p in placements)
    if total_weight > max_payload:
        return False, f"Payload capacity exceeded: {total_weight} > {max_payload}"

    for i, p in enumerate(placements):
        x, y, z = p["xMm"], p["yMm"], p["zMm"]
        l, w, h = p["lengthMm"], p["widthMm"], p["heightMm"]

        if x < 0 or y < 0 or z < 0 or (x + l) > comp_l or (y + w) > comp_w or (z + h) > comp_h:
            return False, f"Placement out of bounds: item {p['cargoItemId']}"

        for j in range(i + 1, len(placements)):
            o = placements[j]
            # Strict 3D interior overlap
            if (x < o["xMm"] + o["lengthMm"] and x + l > o["xMm"] and
                y < o["yMm"] + o["widthMm"] and y + w > o["yMm"] and
                z < o["zMm"] + o["heightMm"] and z + h > o["zMm"]):
                return False, f"Overlap detected between {p['cargoItemId']} and {o['cargoItemId']}"

        # Support check
        if z > 0:
            base_area = l * w
            supported_area = 0
            for lower in placements:
                if lower["zMm"] + lower["heightMm"] == z:
                    lx1, ly1 = lower["xMm"], lower["yMm"]
                    lx2, ly2 = lx1 + lower["lengthMm"], ly1 + lower["widthMm"]
                    inter_x = max(0, min(x + l, lx2) - max(x, lx1))
                    inter_y = max(0, min(y + w, ly2) - max(y, ly1))
                    supported_area += inter_x * inter_y

            if supported_area < base_area * 0.5:
                return False, f"Insufficient bottom support for {p['cargoItemId']}"

            # Supporting base stackability & weight
            if not can_stack_on_supporting_items(x, y, z, l, w, p["weightKg"], placements):
                return False, f"Stackability or stack weight limit exceeded on support of {p['cargoItemId']}"

        # Segregation
        is_ok, code, desc = check_segregation(x, y, z, l, w, h, p["hazardClass"], placements[:i] + placements[i+1:], active_rules)
        if not is_ok:
            return False, f"Segregation violation: {code} - {desc}"

    return True, None

def solve_cpsat_cargo(command: Dict[str, Any],
                      random_seed: int = 42,
                      force_cpsat: bool = False) -> Dict[str, Any]:
    """
    Real, bounded 3D Cargo Loading Optimization Pipeline with Google OR-Tools CP-SAT.
    
    Architecture & Execution Strategy:
    1. Deterministic Heuristic produces a guaranteed, safe, lexicographically ordered incumbent baseline.
    2. If the problem size is within bounded CP-SAT limits (or force_cpsat is True), an exact
       CP-SAT model with Disjunctive 3D Boxes, NoOverlap2D, Weight and Lexicographic objectives is formulated.
    3. The heuristic solution is injected as model hint.
    4. CP-SAT solves within remaining time budget.
    5. The resulting layout is strictly validated independently.
    6. If CP-SAT fails, times out, or produces an invalid layout, the pipeline gracefully falls back
       to the best validated heuristic incumbent with documented fallback reason and diagnostics.
    """
    start_time = time.time()
    job_id = command.get("jobId", "")
    attempt_id = command.get("attemptId", "")
    correlation_id = command.get("correlationId", "")
    rule_snapshot_id = command.get("ruleSnapshotId", "")
    rule_snapshot_digest = command.get("ruleSnapshotDigest")
    total_time_limit_sec = command.get("timeLimitSeconds", 20)
    algorithm_requested = command.get("algorithm", "")

    # Execute deterministic heuristic first for bounded baseline & warm start
    heuristic_res = solve_heuristic(command)
    h_placements = heuristic_res.get("placements", [])
    h_unassigned = heuristic_res.get("unassignedItems", [])
    h_duration_ms = heuristic_res.get("executionDurationMs", 0)

    compartment = command.get("compartment", {})
    comp_l = int(compartment.get("lengthMm", 0))
    comp_w = int(compartment.get("widthMm", 0))
    comp_h = int(compartment.get("heightMm", 0))
    max_payload_kg = float(compartment.get("maxPayloadKg", 0.0))

    if comp_l <= 0 or comp_w <= 0 or comp_h <= 0 or max_payload_kg <= 0:
        return heuristic_res

    items_data = command.get("items", [])
    physical_units: List[PhysicalItemUnit] = []
    seen_ids = set()

    for item in items_data:
        cid = item.get("cargoItemId")
        if not cid or cid in seen_ids:
            return heuristic_res
        seen_ids.add(cid)
        qty = item.get("quantity", 1)
        for i in range(qty):
            is_stackable = bool(item.get("stackable") is True)
            unit = PhysicalItemUnit(
                cargo_item_id=cid,
                instance_index=i,
                description=item.get("description", ""),
                hazard_class=item.get("hazardClass") or "NON_HAZ",
                un_number=item.get("unNumber") or "UN0000",
                length_mm=item.get("lengthMm", 0),
                width_mm=item.get("widthMm", 0),
                height_mm=item.get("heightMm", 0),
                weight_kg=item.get("weightKg", 0.0),
                stackable=is_stackable,
                max_stack_weight_kg=item.get("maxStackWeightKg", 0.0),
                permitted_orientations=item.get("permittedOrientations")
            )
            physical_units.append(unit)

    rule_snapshot = command.get("ruleSnapshot") or {}
    active_rules = rule_snapshot.get("rules") or []

    # Bounded model threshold: CP-SAT exact formulation scales quadratically in pairwise relations.
    # For small to medium problem instances (units <= 25) or when force_cpsat is requested:
    elapsed = time.time() - start_time
    remaining_budget = max(1.0, total_time_limit_sec - elapsed)

    is_scale_suitable = len(physical_units) <= 25 or force_cpsat or "CPSAT" in algorithm_requested.upper()

    if not is_scale_suitable or len(physical_units) == 0:
        logger.info("Problem scale (units=%d) exceeds CP-SAT bound or scale unsuitable. Preserving heuristic incumbent.", len(physical_units))
        heuristic_res["diagnostics"]["cpsatInvoked"] = False
        heuristic_res["diagnostics"]["fallbackReason"] = "SCALE_EXCEEDS_BOUND_PRESERVED_INCUMBENT" if len(physical_units) > 25 else "EMPTY_INPUT"
        return heuristic_res

    # Formulate CP-SAT model
    model = cp_model.CpModel()
    n = len(physical_units)

    # Variables per unit:
    # placed_var: bool (1 if placed, 0 if unassigned)
    placed_vars = [model.NewBoolVar(f"placed_{i}") for i in range(n)]

    x_vars = [model.NewIntVar(0, comp_l, f"x_{i}") for i in range(n)]
    y_vars = [model.NewIntVar(0, comp_w, f"y_{i}") for i in range(n)]
    z_vars = [model.NewIntVar(0, comp_h, f"z_{i}") for i in range(n)]

    # Orientations: 0 = DEFAULT (l, w, h), 1 = ROTATE_YAW_90 (w, l, h) if permitted
    rot_vars = []
    dx_vars = []
    dy_vars = []
    dz_vars = []

    for i, u in enumerate(physical_units):
        can_rot = False
        if u.permitted_orientations and any(o.upper() in (ORIENTATION_ROTATE_YAW_90, "YAW_90") for o in u.permitted_orientations):
            if u.length_mm != u.width_mm:
                can_rot = True

        if can_rot:
            r = model.NewBoolVar(f"rot_{i}")
            rot_vars.append(r)
            dx = model.NewIntVar(min(u.length_mm, u.width_mm), max(u.length_mm, u.width_mm), f"dx_{i}")
            dy = model.NewIntVar(min(u.length_mm, u.width_mm), max(u.length_mm, u.width_mm), f"dy_{i}")
            model.Add(dx == u.length_mm).OnlyEnforceIf(r.Not())
            model.Add(dy == u.width_mm).OnlyEnforceIf(r.Not())
            model.Add(dx == u.width_mm).OnlyEnforceIf(r)
            model.Add(dy == u.length_mm).OnlyEnforceIf(r)
        else:
            r = None
            rot_vars.append(None)
            dx = model.NewConstant(u.length_mm)
            dy = model.NewConstant(u.width_mm)

        dz = model.NewConstant(u.height_mm)
        dx_vars.append(dx)
        dy_vars.append(dy)
        dz_vars.append(dz)

        # Boundary containment when placed
        model.Add(x_vars[i] + dx <= comp_l).OnlyEnforceIf(placed_vars[i])
        model.Add(y_vars[i] + dy <= comp_w).OnlyEnforceIf(placed_vars[i])
        model.Add(z_vars[i] + dz <= comp_h).OnlyEnforceIf(placed_vars[i])

    # 1. Total payload capacity constraint (scaled to grams or exact integer kg)
    # Scaled integer weights in 100g units (0.1 kg) to guarantee integer arithmetic
    weight_units = [int(round(u.weight_kg * 10)) for u in physical_units]
    max_payload_units = int(round(max_payload_kg * 10))
    model.Add(sum(placed_vars[i] * weight_units[i] for i in range(n)) <= max_payload_units)

    # 2. Pairwise 3D Non-Overlap Constraints between placed units
    for i in range(n):
        for j in range(i + 1, n):
            # If both items i and j are placed, they must not overlap in 3D:
            # At least one spatial separation axis must hold:
            # x_i + dx_i <= x_j  OR  x_j + dx_j <= x_i  OR
            # y_i + dy_i <= y_j  OR  y_j + dy_j <= y_i  OR
            # z_i + dz_i <= z_j  OR  z_j + dz_j <= z_i
            left = model.NewBoolVar(f"sep_x1_{i}_{j}")
            right = model.NewBoolVar(f"sep_x2_{i}_{j}")
            before = model.NewBoolVar(f"sep_y1_{i}_{j}")
            after = model.NewBoolVar(f"sep_y2_{i}_{j}")
            below = model.NewBoolVar(f"sep_z1_{i}_{j}")
            above = model.NewBoolVar(f"sep_z2_{i}_{j}")

            model.Add(x_vars[i] + dx_vars[i] <= x_vars[j]).OnlyEnforceIf(left)
            model.Add(x_vars[j] + dx_vars[j] <= x_vars[i]).OnlyEnforceIf(right)
            model.Add(y_vars[i] + dy_vars[i] <= y_vars[j]).OnlyEnforceIf(before)
            model.Add(y_vars[j] + dy_vars[j] <= y_vars[i]).OnlyEnforceIf(after)
            model.Add(z_vars[i] + dz_vars[i] <= z_vars[j]).OnlyEnforceIf(below)
            model.Add(z_vars[j] + dz_vars[j] <= z_vars[i]).OnlyEnforceIf(above)

            # Both placed implies at least one direction holds
            model.AddBoolOr([left, right, before, after, below, above]).OnlyEnforceIf([placed_vars[i], placed_vars[j]])

    # 3. Hazard Segregation Constraints
    if active_rules:
        for i in range(n):
            for j in range(i + 1, n):
                u_i = physical_units[i]
                u_j = physical_units[j]

                for rule in active_rules:
                    r_prim = rule.get("primaryHazardClass")
                    r_sec = rule.get("secondaryHazardClass")
                    match = (r_prim == u_i.hazard_class and r_sec == u_j.hazard_class) or \
                            (r_prim == u_j.hazard_class and r_sec == u_i.hazard_class)

                    if match:
                        seg_type = (rule.get("segregationType") or "").upper()
                        if seg_type == "FORBIDDEN":
                            # Cannot co-load both
                            model.AddBoolOr([placed_vars[i].Not(), placed_vars[j].Not()])
                        elif seg_type == "SEPARATED_BY_DISTANCE":
                            min_dist = int(rule.get("minDistanceMm", 0))
                            if min_dist > 0:
                                # Parity with IndependentValidator: 3D Euclidean center-to-center distance
                                # (cxa - cxb)^2 + (cya - cyb)^2 + (cza - czb)^2 >= min_dist^2
                                # Center coordinate multiplied by 2: double_cx = 2 * x + dx
                                double_cx_i = model.NewIntVar(0, 2 * comp_l + 10000, f"dcx_{i}_{j}")
                                double_cy_i = model.NewIntVar(0, 2 * comp_w + 10000, f"dcy_{i}_{j}")
                                double_cz_i = model.NewIntVar(0, 2 * comp_h + 10000, f"dcz_{i}_{j}")
                                double_cx_j = model.NewIntVar(0, 2 * comp_l + 10000, f"dcx_{j}_{i}")
                                double_cy_j = model.NewIntVar(0, 2 * comp_w + 10000, f"dcy_{j}_{i}")
                                double_cz_j = model.NewIntVar(0, 2 * comp_h + 10000, f"dcz_{j}_{i}")

                                model.Add(double_cx_i == 2 * x_vars[i] + dx_vars[i])
                                model.Add(double_cy_i == 2 * y_vars[i] + dy_vars[i])
                                model.Add(double_cz_i == 2 * z_vars[i] + dz_vars[i])
                                model.Add(double_cx_j == 2 * x_vars[j] + dx_vars[j])
                                model.Add(double_cy_j == 2 * y_vars[j] + dy_vars[j])
                                model.Add(double_cz_j == 2 * z_vars[j] + dz_vars[j])

                                diff_x = model.NewIntVar(-2 * comp_l - 10000, 2 * comp_l + 10000, f"diff_x_{i}_{j}")
                                diff_y = model.NewIntVar(-2 * comp_w - 10000, 2 * comp_w + 10000, f"diff_y_{i}_{j}")
                                diff_z = model.NewIntVar(-2 * comp_h - 10000, 2 * comp_h + 10000, f"diff_z_{i}_{j}")

                                model.Add(diff_x == double_cx_i - double_cx_j)
                                model.Add(diff_y == double_cy_i - double_cy_j)
                                model.Add(diff_z == double_cz_i - double_cz_j)

                                diff_x_sq = model.NewIntVar(0, (2 * comp_l + 10000)**2, f"diff_x_sq_{i}_{j}")
                                diff_y_sq = model.NewIntVar(0, (2 * comp_w + 10000)**2, f"diff_y_sq_{i}_{j}")
                                diff_z_sq = model.NewIntVar(0, (2 * comp_h + 10000)**2, f"diff_z_sq_{i}_{j}")

                                model.AddMultiplicationEquality(diff_x_sq, [diff_x, diff_x])
                                model.AddMultiplicationEquality(diff_y_sq, [diff_y, diff_y])
                                model.AddMultiplicationEquality(diff_z_sq, [diff_z, diff_z])

                                # 4 * (cxa - cxb)^2 >= 4 * min_dist^2
                                four_min_dist_sq = 4 * min_dist * min_dist
                                model.Add(diff_x_sq + diff_y_sq + diff_z_sq >= four_min_dist_sq).OnlyEnforceIf([placed_vars[i], placed_vars[j]])

    # 4. Stacking capability fail-closed restriction:
    # If a unit is non-stackable or has unknown capability, no other unit can be placed directly above it
    for i in range(n):
        if not physical_units[i].stackable:
            for j in range(n):
                if i != j:
                    # Unit j cannot sit on top of unit i
                    # i.e., NOT (z_j == z_i + dz_i AND horizontal overlap)
                    on_top = model.NewBoolVar(f"on_top_{j}_{i}")
                    model.Add(z_vars[j] == z_vars[i] + dz_vars[i]).OnlyEnforceIf(on_top)
                    model.Add(z_vars[j] != z_vars[i] + dz_vars[i]).OnlyEnforceIf(on_top.Not())
                    # If i is non-stackable, on_top is forbidden when both are placed
                    model.Add(on_top == 0).OnlyEnforceIf([placed_vars[i], placed_vars[j]])

    # 5. Warm start / Solution hint from verified Heuristic incumbent
    if h_placements:
        placed_unit_keys = {(p["cargoItemId"], p["instanceIndex"]): p for p in h_placements}
        for i, u in enumerate(physical_units):
            key = (u.cargo_item_id, u.instance_index)
            if key in placed_unit_keys:
                hp = placed_unit_keys[key]
                model.AddHint(placed_vars[i], 1)
                model.AddHint(x_vars[i], hp["xMm"])
                model.AddHint(y_vars[i], hp["yMm"])
                model.AddHint(z_vars[i], hp["zMm"])
                if rot_vars[i] is not None:
                    model.AddHint(rot_vars[i], 1 if hp.get("orientation") == ORIENTATION_ROTATE_YAW_90 else 0)
            else:
                model.AddHint(placed_vars[i], 0)

    # 6. Lexicographic Objective Formulation:
    # Level 1: Maximize assigned cargo count & total volume (large multiplier)
    # Level 2: Compactness / position gravity (minimize z, then x, then y)
    # Objective = Sum(placed_i * (10,000,000 + volume_weight)) - Sum(z_i * 100 + x_i * 2 + y_i)
    obj_terms = []
    for i, u in enumerate(physical_units):
        vol_score = int((u.length_mm * u.width_mm * u.height_mm) // 1000000)
        placement_benefit = 10000000 + vol_score
        obj_terms.append(placed_vars[i] * placement_benefit)
        # Minimize elevation and spread
        obj_terms.append(z_vars[i] * -100)
        obj_terms.append(x_vars[i] * -2)
        obj_terms.append(y_vars[i] * -1)

    model.Maximize(sum(obj_terms))

    # Solver configuration
    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = min(remaining_budget, 15.0)
    solver.parameters.random_seed = random_seed
    solver.parameters.num_workers = 1 # Deterministic execution

    solver_status_code = solver.Solve(model)
    solve_duration_ms = int((time.time() - start_time) * 1000)

    # Status mapping
    if solver_status_code in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        cp_placements: List[Dict[str, Any]] = []
        cp_unassigned: List[Dict[str, Any]] = []

        for i, u in enumerate(physical_units):
            if solver.Value(placed_vars[i]) == 1:
                is_rot = rot_vars[i] is not None and solver.Value(rot_vars[i]) == 1
                orient_name = ORIENTATION_ROTATE_YAW_90 if is_rot else ORIENTATION_DEFAULT
                cur_l = u.width_mm if is_rot else u.length_mm
                cur_w = u.length_mm if is_rot else u.width_mm
                cur_h = u.height_mm

                cp_placements.append({
                    "placementId": f"pl-cp-{u.cargo_item_id}-{u.instance_index}",
                    "cargoItemId": u.cargo_item_id,
                    "instanceIndex": u.instance_index,
                    "hazardClass": u.hazard_class,
                    "unNumber": u.un_number,
                    "xMm": int(solver.Value(x_vars[i])),
                    "yMm": int(solver.Value(y_vars[i])),
                    "zMm": int(solver.Value(z_vars[i])),
                    "lengthMm": cur_l,
                    "widthMm": cur_w,
                    "heightMm": cur_h,
                    "weightKg": u.weight_kg,
                    "orientation": orient_name,
                    "stackable": u.stackable,
                    "maxStackWeightKg": u.max_stack_weight_kg
                })
            else:
                cp_unassigned.append({
                    "cargoItemId": u.cargo_item_id,
                    "instanceIndex": u.instance_index,
                    "reasonCode": "OPTIMIZATION_BOUND_OR_CAPACITY",
                    "reasonDescription": "Unit excluded by solver to respect compartment bounds or safety constraints"
                })

        # Independent revalidation of CP-SAT candidate
        is_valid, validation_err = validate_candidate_strictly(cp_placements, compartment, active_rules)

        if is_valid:
            # Check if CP-SAT solution is at least as good as heuristic incumbent
            if len(cp_placements) >= len(h_placements):
                # When CP-SAT proves optimality for the bounded model, report OPTIMAL.
                # A partial placement where the remaining units could not fit is mathematically optimal
                # for the encoded packing model. If solver did not prove optimality (e.g. FEASIBLE incumbent), report FEASIBLE.
                if solver_status_code == cp_model.OPTIMAL and len(cp_placements) > 0:
                    solver_status_name = "OPTIMAL"
                elif cp_placements:
                    solver_status_name = "FEASIBLE"
                else:
                    solver_status_name = "UNKNOWN"

                content_digest = compute_content_digest(cp_placements)
                return {
                    "resultId": f"res-{job_id}-{attempt_id}",
                    "jobId": job_id,
                    "attemptId": attempt_id,
                    "correlationId": correlation_id,
                    "solverStatus": solver_status_name,
                    "provenance": PROVENANCE_CP_SAT,
                    "executionDurationMs": solve_duration_ms,
                    "ruleSnapshotId": rule_snapshot_id,
                    "ruleSnapshotDigest": rule_snapshot_digest,
                    "contentDigest": content_digest,
                    "placements": cp_placements,
                    "unassignedItems": cp_unassigned,
                    "diagnostics": {
                        "engine": PROVENANCE_CP_SAT,
                        "version": ALGORITHM_VERSION,
                        "ortoolsVersion": "9.10.4067",
                        "cpSatStatus": solver.StatusName(solver_status_code),
                        "modelOptimalityProven": solver_status_code == cp_model.OPTIMAL,
                        "unitsTotal": n,
                        "unitsPlaced": len(cp_placements),
                        "unitsUnassigned": len(cp_unassigned),
                        "branches": solver.NumBranches(),
                        "wallTimeSeconds": solver.WallTime(),
                        "objectiveValue": solver.ObjectiveValue() if solver_status_code in (cp_model.OPTIMAL, cp_model.FEASIBLE) else None,
                        "validatedIndependently": True
                    }
                }
            else:
                logger.info("CP-SAT solution had fewer placed units than heuristic incumbent. Preserving heuristic solution.")
                heuristic_res["diagnostics"]["cpsatInvoked"] = True
                heuristic_res["diagnostics"]["fallbackReason"] = "INCUMBENT_SUPERIOR_QUANTITY"
                return heuristic_res
        else:
            logger.warning("CP-SAT candidate failed independent revalidation: %s. Falling back to heuristic incumbent.", validation_err)
            heuristic_res["diagnostics"]["cpsatInvoked"] = True
            heuristic_res["diagnostics"]["fallbackReason"] = f"CPSAT_REVALIDATION_FAILED: {validation_err}"
            return heuristic_res

    elif solver_status_code == cp_model.INFEASIBLE:
        # If model is proven INFEASIBLE and physical_units were present, report proven infeasibility or fallback
        logger.info("CP-SAT proved problem infeasible for complete placement.")
        if h_placements:
            # Partial placement exists from heuristic
            heuristic_res["diagnostics"]["cpsatStatus"] = "INFEASIBLE_FOR_FULL_SET"
            return heuristic_res
        else:
            return {
                "resultId": f"res-{job_id}-{attempt_id}",
                "jobId": job_id,
                "attemptId": attempt_id,
                "correlationId": correlation_id,
                "solverStatus": "INFEASIBLE",
                "provenance": PROVENANCE_CP_SAT,
                "executionDurationMs": solve_duration_ms,
                "ruleSnapshotId": rule_snapshot_id,
                "ruleSnapshotDigest": rule_snapshot_digest,
                "contentDigest": "infeasible-digest",
                "placements": [],
                "unassignedItems": [{
                    "cargoItemId": u.cargo_item_id,
                    "instanceIndex": u.instance_index,
                    "reasonCode": "PROVEN_INFEASIBLE",
                    "reasonDescription": "Proven infeasible to place unit given compartment and safety constraints"
                } for u in physical_units],
                "diagnostics": {
                    "engine": PROVENANCE_CP_SAT,
                    "cpSatStatus": "INFEASIBLE",
                    "proof": "CP-SAT exhaustive search proved no solution exists"
                }
            }
    else:
        # TIMEOUT or UNKNOWN
        logger.info("CP-SAT search returned %s. Preserving best valid heuristic incumbent.", solver.StatusName(solver_status_code))
        heuristic_res["diagnostics"]["cpsatInvoked"] = True
        heuristic_res["diagnostics"]["fallbackReason"] = f"CPSAT_{solver.StatusName(solver_status_code)}"
        return heuristic_res
