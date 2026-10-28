import math
import hashlib
import json
import time
from typing import List, Dict, Any, Optional, Set, Tuple

ORIENTATION_DEFAULT = "DEFAULT"
ORIENTATION_ROTATE_YAW_90 = "ROTATE_YAW_90"
ALGORITHM_PROVENANCE = "PYTHON_HEURISTIC_EP_3D_V1"
ALGORITHM_VERSION = "1.0.0-m2"

class PhysicalItemUnit:
    def __init__(self, cargo_item_id: str, instance_index: int, description: str,
                 hazard_class: str, un_number: str, length_mm: int, width_mm: int,
                 height_mm: int, weight_kg: float, stackable: bool, max_stack_weight_kg: float,
                 permitted_orientations: Optional[List[str]]):
        self.cargo_item_id = cargo_item_id
        self.instance_index = instance_index
        self.description = description
        self.hazard_class = hazard_class
        self.un_number = un_number
        self.length_mm = int(length_mm)
        self.width_mm = int(width_mm)
        self.height_mm = int(height_mm)
        self.weight_kg = float(weight_kg)
        self.stackable = bool(stackable)
        self.max_stack_weight_kg = float(max_stack_weight_kg) if max_stack_weight_kg is not None else 0.0
        self.permitted_orientations = permitted_orientations or [ORIENTATION_DEFAULT]

class OrientationDimensions:
    def __init__(self, orientation_name: str, length_mm: int, width_mm: int, height_mm: int):
        self.orientation_name = orientation_name
        self.length_mm = length_mm
        self.width_mm = width_mm
        self.height_mm = height_mm

def get_permitted_orientation_dimensions(unit: PhysicalItemUnit) -> List[OrientationDimensions]:
    orientations = [OrientationDimensions(ORIENTATION_DEFAULT, unit.length_mm, unit.width_mm, unit.height_mm)]
    if unit.permitted_orientations:
        if any(o.upper() in (ORIENTATION_ROTATE_YAW_90, "YAW_90") for o in unit.permitted_orientations):
            if unit.length_mm != unit.width_mm:
                orientations.append(OrientationDimensions(ORIENTATION_ROTATE_YAW_90, unit.width_mm, unit.length_mm, unit.height_mm))
    return orientations

def has_3d_collision(x: int, y: int, z: int, length: int, width: int, height: int, placed_items: List[Dict[str, Any]]) -> bool:
    x2, y2, z2 = x + length, y + width, z + height
    for other in placed_items:
        ox1 = other["xMm"]
        oy1 = other["yMm"]
        oz1 = other["zMm"]
        ox2 = ox1 + other["lengthMm"]
        oy2 = oy1 + other["widthMm"]
        oz2 = oz1 + other["heightMm"]

        # Strict interior overlap
        if x < ox2 and x2 > ox1 and y < oy2 and y2 > oy1 and z < oz2 and z2 > oz1:
            return True
    return False

def is_supported(x: int, y: int, z: int, length: int, width: int, height: int, placed_items: List[Dict[str, Any]]) -> bool:
    if z == 0:
        return True
    x2, y2 = x + length, y + width
    total_base_area = length * width
    supported_area = 0

    for lower in placed_items:
        lower_top = lower["zMm"] + lower["heightMm"]
        if lower_top == z:
            lx1 = lower["xMm"]
            ly1 = lower["yMm"]
            lx2 = lx1 + lower["lengthMm"]
            ly2 = ly1 + lower["widthMm"]

            inter_x = max(0, min(x2, lx2) - max(x, lx1))
            inter_y = max(0, min(y2, ly2) - max(y, ly1))
            supported_area += (inter_x * inter_y)

    return supported_area >= (total_base_area * 0.5)

def compute_current_stacked_weight_on(base: Dict[str, Any], placed_items: List[Dict[str, Any]]) -> float:
    weight = 0.0
    bx1 = base["xMm"]
    by1 = base["yMm"]
    bx2 = bx1 + base["lengthMm"]
    by2 = by1 + base["widthMm"]
    b_top = base["zMm"] + base["heightMm"]

    for item in placed_items:
        if item["zMm"] >= b_top:
            ix1 = item["xMm"]
            iy1 = item["yMm"]
            ix2 = ix1 + item["lengthMm"]
            iy2 = iy1 + item["widthMm"]
            if ix1 < bx2 and ix2 > bx1 and iy1 < by2 and iy2 > by1:
                weight += item["weightKg"]
    return weight

def can_stack_on_supporting_items(x: int, y: int, z: int, length: int, width: int, unit_weight: float, placed_items: List[Dict[str, Any]]) -> bool:
    if z == 0:
        return True
    x2, y2 = x + length, y + width
    for lower in placed_items:
        lower_top = lower["zMm"] + lower["heightMm"]
        if lower_top == z:
            lx1 = lower["xMm"]
            ly1 = lower["yMm"]
            lx2 = lx1 + lower["lengthMm"]
            ly2 = ly1 + lower["widthMm"]

            if x < lx2 and x2 > lx1 and y < ly2 and y2 > ly1:
                # Fail-closed check: stackable must be explicitly True
                if not lower.get("stackable"):
                    return False
                max_w = lower.get("maxStackWeightKg")
                if max_w is not None and max_w > 0:
                    current_stacked = compute_current_stacked_weight_on(lower, placed_items)
                    if current_stacked + unit_weight > max_w:
                        return False
    return True

def check_segregation(x: int, y: int, z: int, length: int, width: int, height: int,
                      hazard_class: str, placed_items: List[Dict[str, Any]], rules: List[Dict[str, Any]]) -> Tuple[bool, Optional[str], Optional[str]]:
    if not rules:
        return True, None, None

    cxa = x + (length / 2.0)
    cya = y + (width / 2.0)
    cza = z + (height / 2.0)

    for placed in placed_items:
        cxb = placed["xMm"] + (placed["lengthMm"] / 2.0)
        cyb = placed["yMm"] + (placed["widthMm"] / 2.0)
        czb = placed["zMm"] + (placed["heightMm"] / 2.0)

        for rule in rules:
            r_prim = rule.get("primaryHazardClass")
            r_sec = rule.get("secondaryHazardClass")
            match = (r_prim == hazard_class and r_sec == placed.get("hazardClass")) or \
                    (r_prim == placed.get("hazardClass") and r_sec == hazard_class)

            if match:
                seg_type = rule.get("segregationType", "")
                if seg_type.upper() == "FORBIDDEN":
                    return False, "HAZARD_COLOAD_FORBIDDEN", f"Co-loading Class {hazard_class} with Class {placed.get('hazardClass')} is forbidden by rule {rule.get('ruleCode')}"
                elif seg_type.upper() == "SEPARATED_BY_DISTANCE":
                    min_dist = rule.get("minDistanceMm", 0)
                    dist = math.sqrt((cxa - cxb)**2 + (cya - cyb)**2 + (cza - czb)**2)
                    if dist < min_dist:
                        return False, "HAZARD_SEGREGATION_VIOLATION", f"Class {hazard_class} and Class {placed.get('hazardClass')} center distance ({int(dist)} mm) is below required {min_dist} mm by rule {rule.get('ruleCode')}"

    return True, None, None

def add_point_if_valid(p: Tuple[int, int, int], points: Set[Tuple[int, int, int]], comp_l: int, comp_w: int, comp_h: int, placed_items: List[Dict[str, Any]]):
    x, y, z = p
    if x >= comp_l or y >= comp_w or z >= comp_h:
        return
    for item in placed_items:
        if (item["xMm"] <= x < item["xMm"] + item["lengthMm"] and
            item["yMm"] <= y < item["yMm"] + item["widthMm"] and
            item["zMm"] <= z < item["zMm"] + item["heightMm"]):
            return
    points.add((x, y, z))

def compute_content_digest(placements: List[Dict[str, Any]]) -> str:
    if not placements:
        return hashlib.sha256(b"[]").hexdigest()
    canonical_list = []
    for p in placements:
        canonical_list.append({
            "cargoItemId": str(p.get("cargoItemId") or ""),
            "hazardClass": str(p.get("hazardClass") or ""),
            "heightMm": int(p.get("heightMm", 0)),
            "instanceIndex": int(p.get("instanceIndex", 0)),
            "lengthMm": int(p.get("lengthMm", 0)),
            "maxStackWeightKg": float(p.get("maxStackWeightKg")) if p.get("maxStackWeightKg") is not None else None,
            "orientation": str(p.get("orientation") or "DEFAULT"),
            "placementId": str(p.get("placementId") or ""),
            "stackable": bool(p.get("stackable")) if p.get("stackable") is not None else False,
            "unNumber": str(p.get("unNumber") or ""),
            "weightKg": float(p.get("weightKg", 0.0)),
            "widthMm": int(p.get("widthMm", 0)),
            "xMm": int(p.get("xMm", 0)),
            "yMm": int(p.get("yMm", 0)),
            "zMm": int(p.get("zMm", 0))
        })
    serialized = json.dumps(canonical_list, separators=(',', ':'), sort_keys=True)
    return hashlib.sha256(serialized.encode('utf-8')).hexdigest()

SUPPORTED_ALGORITHMS = {
    "HEURISTIC_EP_3D_V1",
    "PYTHON_HEURISTIC_EP_3D_V1",
    "PYTHON_ORTOOLS_CPSAT_3D_V1",
    "ORTOOLS_CPSAT_3D_V1"
}

def solve_3d_packing(command: Dict[str, Any]) -> Dict[str, Any]:
    start_time = time.time()
    job_id = command.get("jobId", "")
    attempt_id = command.get("attemptId", "")
    correlation_id = command.get("correlationId", "")
    rule_snapshot_id = command.get("ruleSnapshotId", "")
    rule_snapshot_digest = command.get("ruleSnapshotDigest")
    time_limit_sec = command.get("timeLimitSeconds", 20)
    algorithm_requested = command.get("algorithm", "")
    if algorithm_requested and algorithm_requested.strip() not in SUPPORTED_ALGORITHMS:
        raise ValueError(f"Unsupported algorithm identifier: {algorithm_requested}")

    compartment = command.get("compartment", {})
    comp_l = int(compartment.get("lengthMm", 0))
    comp_w = int(compartment.get("widthMm", 0))
    comp_h = int(compartment.get("heightMm", 0))
    max_payload_kg = float(compartment.get("maxPayloadKg", 0.0))

    if comp_l <= 0 or comp_w <= 0 or comp_h <= 0 or max_payload_kg <= 0:
        return {
            "resultId": f"res-err-{job_id}",
            "jobId": job_id,
            "attemptId": attempt_id,
            "correlationId": correlation_id,
            "solverStatus": "UNKNOWN",
            "provenance": ALGORITHM_PROVENANCE,
            "executionDurationMs": 0,
            "errorMessage": "Invalid compartment dimensions or payload capacity",
            "placements": [],
            "unassignedItems": []
        }

    items_data = command.get("items", [])
    physical_units: List[PhysicalItemUnit] = []
    seen_ids = set()

    for item in items_data:
        cid = item.get("cargoItemId")
        if not cid or cid in seen_ids:
            return {
                "resultId": f"res-err-{job_id}",
                "jobId": job_id,
                "attemptId": attempt_id,
                "correlationId": correlation_id,
                "solverStatus": "UNKNOWN",
                "provenance": ALGORITHM_PROVENANCE,
                "executionDurationMs": 0,
                "errorMessage": f"Duplicate or missing cargoItemId: {cid}",
                "placements": [],
                "unassignedItems": []
            }
        seen_ids.add(cid)
        qty = item.get("quantity", 1)
        for i in range(qty):
            # Fail closed on stackable: requires explicitly True
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

    # Sort units deterministically: volume desc, weight desc, id asc, instance asc
    physical_units.sort(
        key=lambda u: (-(u.length_mm * u.width_mm * u.height_mm), -u.weight_kg, u.cargo_item_id, u.instance_index)
    )

    rule_snapshot = command.get("ruleSnapshot") or {}
    active_rules = rule_snapshot.get("rules") or []

    placed_items: List[Dict[str, Any]] = []
    unassigned_items: List[Dict[str, Any]] = []
    extreme_points: Set[Tuple[int, int, int]] = {(0, 0, 0)}

    current_weight = 0.0
    evaluations_count = 0
    timeout_occurred = False

    for unit in physical_units:
        # Check timeout budget
        if time.time() - start_time > time_limit_sec:
            timeout_occurred = True
            unassigned_items.append({
                "cargoItemId": unit.cargo_item_id,
                "instanceIndex": unit.instance_index,
                "reasonCode": "EXECUTION_TIMEOUT",
                "reasonDescription": "Execution exceeded allocated solver budget"
            })
            continue

        if current_weight + unit.weight_kg > max_payload_kg:
            unassigned_items.append({
                "cargoItemId": unit.cargo_item_id,
                "instanceIndex": unit.instance_index,
                "reasonCode": "PAYLOAD_CAPACITY_EXCEEDED",
                "reasonDescription": f"Placement exceeds compartment maximum payload capacity of {max_payload_kg} kg"
            })
            continue

        valid_candidates = []
        primary_reason = "NO_FIT_GEOMETRY"
        primary_desc = "No feasible position found within compartment boundaries"

        orientations = get_permitted_orientation_dimensions(unit)
        sorted_ep = sorted(extreme_points, key=lambda p: (p[2], p[0], p[1]))

        for ep in sorted_ep:
            ex, ey, ez = ep
            for orient in orientations:
                evaluations_count += 1
                # 1. Boundary containment
                if ex + orient.length_mm > comp_l or ey + orient.width_mm > comp_w or ez + orient.height_mm > comp_h:
                    continue

                # 2. Collision
                if has_3d_collision(ex, ey, ez, orient.length_mm, orient.width_mm, orient.height_mm, placed_items):
                    continue

                # 3. Support
                if not is_supported(ex, ey, ez, orient.length_mm, orient.width_mm, orient.height_mm, placed_items):
                    primary_reason = "UNSUPPORTED_STACKING"
                    primary_desc = "Unit cannot float without bottom support from floor or stackable cargo"
                    continue

                # 4. Stacking compatibility & weight
                if not can_stack_on_supporting_items(ex, ey, ez, orient.length_mm, orient.width_mm, unit.weight_kg, placed_items):
                    primary_reason = "STACKING_WEIGHT_OR_UNSTACKABLE"
                    primary_desc = "Supporting base item is not stackable or max stack weight would be exceeded"
                    continue

                # 5. Segregation
                is_allowed, code, desc = check_segregation(
                    ex, ey, ez, orient.length_mm, orient.width_mm, orient.height_mm, unit.hazard_class, placed_items, active_rules
                )
                if not is_allowed:
                    primary_reason = code or "HAZARD_SEGREGATION_VIOLATION"
                    primary_desc = desc or "Segregation violation"
                    continue

                # Score: Compactness (z*1M + x*1k + y + penalty)
                penalty = 0.0 if orient.orientation_name == ORIENTATION_DEFAULT else 10.0
                score = (ez * 1000000.0) + (ex * 1000.0) + ey + penalty
                valid_candidates.append((score, ep, orient))

        if not valid_candidates:
            unassigned_items.append({
                "cargoItemId": unit.cargo_item_id,
                "instanceIndex": unit.instance_index,
                "reasonCode": primary_reason,
                "reasonDescription": primary_desc
            })
        else:
            valid_candidates.sort(key=lambda c: c[0])
            best_score, best_ep, best_orient = valid_candidates[0]
            placement = {
                "placementId": f"pl-{unit.cargo_item_id}-{unit.instance_index}-{len(placed_items) + 1}",
                "cargoItemId": unit.cargo_item_id,
                "instanceIndex": unit.instance_index,
                "hazardClass": unit.hazard_class,
                "unNumber": unit.un_number,
                "xMm": best_ep[0],
                "yMm": best_ep[1],
                "zMm": best_ep[2],
                "lengthMm": best_orient.length_mm,
                "widthMm": best_orient.width_mm,
                "heightMm": best_orient.height_mm,
                "weightKg": unit.weight_kg,
                "orientation": best_orient.orientation_name,
                "stackable": unit.stackable,
                "maxStackWeightKg": unit.max_stack_weight_kg
            }
            placed_items.append(placement)
            current_weight += unit.weight_kg

            # Add new candidate extreme points
            bx, by, bz = best_ep
            add_point_if_valid((bx + best_orient.length_mm, by, bz), extreme_points, comp_l, comp_w, comp_h, placed_items)
            add_point_if_valid((bx, by + best_orient.width_mm, bz), extreme_points, comp_l, comp_w, comp_h, placed_items)
            add_point_if_valid((bx, by, bz + best_orient.height_mm), extreme_points, comp_l, comp_w, comp_h, placed_items)

            for rule in active_rules:
                if rule.get("segregationType", "").upper() == "SEPARATED_BY_DISTANCE" and rule.get("minDistanceMm", 0) > 0:
                    dist = rule.get("minDistanceMm")
                    add_point_if_valid((bx + best_orient.length_mm + dist, by, bz), extreme_points, comp_l, comp_w, comp_h, placed_items)
                    add_point_if_valid((bx, by + best_orient.width_mm + dist, bz), extreme_points, comp_l, comp_w, comp_h, placed_items)

    duration_ms = int((time.time() - start_time) * 1000)

    require_all_placed = bool(command.get("requireAllPlaced", False))

    # Determine status: never claim OPTIMAL
    if timeout_occurred:
        solver_status = "TIMEOUT"
    elif not placed_items and physical_units:
        solver_status = "UNKNOWN"
    elif require_all_placed and unassigned_items:
        # Complete placement was strictly mandated, but heuristic could only partially assign items
        # Heuristic cannot prove mathematical infeasibility, so it reports UNKNOWN
        solver_status = "UNKNOWN"
    else:
        solver_status = "FEASIBLE"

    content_digest = compute_content_digest(placed_items)

    diagnostics = {
        "engine": ALGORITHM_PROVENANCE,
        "version": ALGORITHM_VERSION,
        "unitsTotal": len(physical_units),
        "unitsPlaced": len(placed_items),
        "unitsUnassigned": len(unassigned_items),
        "evaluations": evaluations_count,
        "ruleSnapshotDigest": rule_snapshot_digest or "N/A",
        "requestedAlgorithm": algorithm_requested or ALGORITHM_PROVENANCE,
        "effectiveAlgorithm": ALGORITHM_PROVENANCE,
        "modelOptimalityProven": False,
        "modelInfeasibilityProven": False,
        "primaryObjectiveProven": False,
        "volumeObjectiveProven": False,
        "compactnessObjectiveProven": False,
        "fullLexicographicOptimalityProven": False
    }

    result = {
        "resultId": f"res-{job_id}-{attempt_id}",
        "jobId": job_id,
        "attemptId": attempt_id,
        "correlationId": correlation_id,
        "solverStatus": solver_status,
        "provenance": ALGORITHM_PROVENANCE,
        "executionDurationMs": duration_ms,
        "ruleSnapshotId": rule_snapshot_id,
        "ruleSnapshotDigest": rule_snapshot_digest,
        "contentDigest": content_digest,
        "placements": placed_items,
        "unassignedItems": unassigned_items,
        "diagnostics": diagnostics
    }
    return result
