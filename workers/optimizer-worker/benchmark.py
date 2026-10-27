"""
Reproducible Benchmark Suite comparing Deterministic Heuristic vs Google OR-Tools CP-SAT
Across 8 Canonical Scenarios:
1. Small cases with known expected outcomes
2. Capacity and boundary cases
3. Rotatable and non-rotatable cargo
4. Mixed hazard classes with forbidden co-loading
5. Minimum-separation cases
6. Stacking and unknown-stackability cases
7. Partial-placement cases
8. Larger synthetic scale cases
"""

import time
import json
import logging
from typing import Dict, Any, List

from heuristic_solver import solve_3d_packing as solve_heuristic
from cpsat_solver import solve_cpsat_cargo, validate_candidate_strictly

# Configure quiet logging
logging.basicConfig(level=logging.WARNING)

BENCHMARK_SCENARIOS: List[Dict[str, Any]] = [
    {
        "name": "1. Small Known Outcome (2 perfect fit boxes)",
        "command": {
            "jobId": "bench-1",
            "attemptId": "att-1",
            "compartment": {"compartmentId": "c1", "lengthMm": 2000, "widthMm": 1000, "heightMm": 1000, "maxPayloadKg": 2000.0},
            "ruleSnapshot": {"rules": []},
            "items": [
                {"cargoItemId": "box1", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1, "stackable": True},
                {"cargoItemId": "box2", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1, "stackable": True}
            ]
        }
    },
    {
        "name": "2. Payload Boundary (weight limit allows only 2 of 3 items)",
        "command": {
            "jobId": "bench-2",
            "attemptId": "att-1",
            "compartment": {"compartmentId": "c1", "lengthMm": 5000, "widthMm": 2000, "heightMm": 2000, "maxPayloadKg": 250.0},
            "ruleSnapshot": {"rules": []},
            "items": [
                {"cargoItemId": "heavy1", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1, "stackable": True},
                {"cargoItemId": "heavy2", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1, "stackable": True},
                {"cargoItemId": "heavy3", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1, "stackable": True}
            ]
        }
    },
    {
        "name": "3. Rotatable Yaw 90 Cargo (fits only when rotated)",
        "command": {
            "jobId": "bench-3",
            "attemptId": "att-1",
            "compartment": {"compartmentId": "c1", "lengthMm": 1500, "widthMm": 2500, "heightMm": 2000, "maxPayloadKg": 5000.0},
            "ruleSnapshot": {"rules": []},
            "items": [
                {
                    "cargoItemId": "wide-box",
                    "lengthMm": 2200,
                    "widthMm": 1200,
                    "heightMm": 1000,
                    "weightKg": 300.0,
                    "quantity": 1,
                    "permittedOrientations": ["ROTATE_YAW_90"],
                    "stackable": True
                }
            ]
        }
    },
    {
        "name": "4. Mixed Hazards Forbidden Co-loading (Class 1 vs Class 2.1)",
        "command": {
            "jobId": "bench-4",
            "attemptId": "att-1",
            "compartment": {"compartmentId": "c1", "lengthMm": 4000, "widthMm": 2000, "heightMm": 2000, "maxPayloadKg": 5000.0},
            "ruleSnapshot": {
                "rules": [
                    {"ruleId": "r1", "ruleCode": "FORBID-1-2.1", "primaryHazardClass": "1", "secondaryHazardClass": "2.1", "segregationType": "FORBIDDEN"}
                ]
            },
            "items": [
                {"cargoItemId": "explosive", "hazardClass": "1", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1, "stackable": True},
                {"cargoItemId": "gas", "hazardClass": "2.1", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1, "stackable": True}
            ]
        }
    },
    {
        "name": "5. Distance Segregation (minDistance 3000 mm)",
        "command": {
            "jobId": "bench-5",
            "attemptId": "att-1",
            "compartment": {"compartmentId": "c1", "lengthMm": 8000, "widthMm": 2400, "heightMm": 2400, "maxPayloadKg": 10000.0},
            "ruleSnapshot": {
                "rules": [
                    {"ruleId": "r2", "ruleCode": "SEP-3-8", "primaryHazardClass": "3", "secondaryHazardClass": "8", "segregationType": "SEPARATED_BY_DISTANCE", "minDistanceMm": 3000}
                ]
            },
            "items": [
                {"cargoItemId": "flammable", "hazardClass": "3", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 150.0, "quantity": 1, "stackable": True},
                {"cargoItemId": "corrosive", "hazardClass": "8", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 150.0, "quantity": 1, "stackable": True}
            ]
        }
    },
    {
        "name": "6. Stacking Fail-Closed (non-stackable base prevents 2nd placement)",
        "command": {
            "jobId": "bench-6",
            "attemptId": "att-1",
            "compartment": {"compartmentId": "c-tight", "lengthMm": 1000, "widthMm": 1000, "heightMm": 2500, "maxPayloadKg": 5000.0},
            "ruleSnapshot": {"rules": []},
            "items": [
                {"cargoItemId": "fragile-1", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1, "stackable": False},
                {"cargoItemId": "fragile-2", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1, "stackable": False}
            ]
        }
    },
    {
        "name": "7. Partial Placement & Infeasible (4 items request, only 2 fit)",
        "command": {
            "jobId": "bench-7",
            "attemptId": "att-1",
            "compartment": {"compartmentId": "c1", "lengthMm": 2000, "widthMm": 1000, "heightMm": 1000, "maxPayloadKg": 5000.0},
            "ruleSnapshot": {"rules": []},
            "items": [
                {"cargoItemId": "box-fit", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 4, "stackable": False}
            ]
        }
    },
    {
        "name": "8. Synthetic Scale (12 items 3D bin packing)",
        "command": {
            "jobId": "bench-8",
            "attemptId": "att-1",
            "timeLimitSeconds": 5,
            "compartment": {"compartmentId": "c1", "lengthMm": 6000, "widthMm": 2400, "heightMm": 2400, "maxPayloadKg": 15000.0},
            "ruleSnapshot": {"rules": []},
            "items": [
                {"cargoItemId": "crate-A", "lengthMm": 1200, "widthMm": 800, "heightMm": 1000, "weightKg": 250.0, "quantity": 6, "stackable": True},
                {"cargoItemId": "crate-B", "lengthMm": 1000, "widthMm": 1000, "heightMm": 800, "weightKg": 200.0, "quantity": 6, "stackable": True}
            ]
        }
    },
    {
        "name": "9. Larger Synthetic Scale (24 items bounded packing)",
        "command": {
            "jobId": "bench-9",
            "attemptId": "att-1",
            "timeLimitSeconds": 4,
            "compartment": {"compartmentId": "c1", "lengthMm": 12000, "widthMm": 2400, "heightMm": 2400, "maxPayloadKg": 25000.0},
            "ruleSnapshot": {"rules": []},
            "items": [
                {"cargoItemId": "pallet-1", "lengthMm": 1200, "widthMm": 800, "heightMm": 1000, "weightKg": 300.0, "quantity": 12, "stackable": True},
                {"cargoItemId": "pallet-2", "lengthMm": 1000, "widthMm": 1000, "heightMm": 900, "weightKg": 250.0, "quantity": 12, "stackable": True}
            ]
        }
    }
]

def run_benchmarks():
    print("=" * 125)
    print(f"{'SCENARIO':<34} | {'SOLVER':<10} | {'STATUS':<9} | {'PLACED':<7} | {'TIME(ms)':<9} | {'OBJECTIVE':<12} | {'VALID':<6} | {'PROVENANCE'}")
    print("=" * 125)

    results = []

    for scen in BENCHMARK_SCENARIOS:
        name = scen["name"]
        cmd = scen["command"]
        comp = cmd["compartment"]
        rules = cmd.get("ruleSnapshot", {}).get("rules", [])

        # 1. Run Deterministic Heuristic
        t0 = time.perf_counter()
        h_res = solve_heuristic(cmd)
        t_h_ms = round((time.perf_counter() - t0) * 1000, 2)
        h_valid, h_err = validate_candidate_strictly(h_res.get("placements", []), comp, rules)
        h_obj = sum(10000000 + int((p["lengthMm"]*p["widthMm"]*p["heightMm"])//1000000) for p in h_res.get("placements", []))
        
        print(f"{name:<34} | {'HEURISTIC':<10} | {h_res.get('solverStatus', 'N/A'):<9} | "
              f"{len(h_res.get('placements', [])):<7} | {t_h_ms:<9} | {h_obj:<12} | {str(h_valid):<6} | {h_res.get('provenance')}")

        # 2. Run Google OR-Tools CP-SAT Pipeline
        t0 = time.perf_counter()
        cp_res = solve_cpsat_cargo(cmd, force_cpsat=True)
        t_cp_ms = round((time.perf_counter() - t0) * 1000, 2)
        cp_valid, cp_err = validate_candidate_strictly(cp_res.get("placements", []), comp, rules)
        cp_obj = cp_res.get("diagnostics", {}).get("objectiveValue")
        cp_obj_str = str(int(cp_obj)) if cp_obj is not None else "N/A"

        print(f"{'':<34} | {'CP-SAT':<10} | {cp_res.get('solverStatus', 'N/A'):<9} | "
              f"{len(cp_res.get('placements', [])):<7} | {t_cp_ms:<9} | {cp_obj_str:<12} | {str(cp_valid):<6} | {cp_res.get('provenance')}")
        print("-" * 125)

        results.append({
            "scenario": name,
            "heuristic": {
                "status": h_res.get("solverStatus"),
                "placed": len(h_res.get("placements", [])),
                "time_ms": t_h_ms,
                "objective": h_obj,
                "valid": h_valid,
                "provenance": h_res.get("provenance")
            },
            "cpsat": {
                "status": cp_res.get("solverStatus"),
                "placed": len(cp_res.get("placements", [])),
                "time_ms": t_cp_ms,
                "objective": cp_obj,
                "valid": cp_valid,
                "provenance": cp_res.get("provenance")
            }
        })

    return results

if __name__ == "__main__":
    run_benchmarks()
