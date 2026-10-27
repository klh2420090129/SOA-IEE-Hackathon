import pytest
from cpsat_solver import solve_cpsat_cargo, validate_candidate_strictly

def test_cpsat_solver_interface_and_status_optimal():
    cmd = {
        "commandId": "cmd-cp-1",
        "jobId": "job-cp-1",
        "attemptId": "att-1",
        "timeLimitSeconds": 15,
        "compartment": {
            "compartmentId": "comp-1",
            "lengthMm": 4000,
            "widthMm": 2400,
            "heightMm": 2600,
            "maxPayloadKg": 10000.0
        },
        "items": [
            {
                "cargoItemId": "box-a",
                "lengthMm": 1000,
                "widthMm": 800,
                "heightMm": 600,
                "weightKg": 200.0,
                "stackable": True,
                "maxStackWeightKg": 500.0,
                "quantity": 2
            }
        ]
    }

    res = solve_cpsat_cargo(cmd, force_cpsat=True)
    assert res["solverStatus"] in ("OPTIMAL", "FEASIBLE")
    assert res["provenance"] == "PYTHON_ORTOOLS_CPSAT_3D_V1"
    assert len(res["placements"]) == 2
    assert len(res["unassignedItems"]) == 0
    
    # Verify candidate passes independent strict validation
    is_valid, err = validate_candidate_strictly(res["placements"], cmd["compartment"], [])
    assert is_valid, f"Validation failed: {err}"

def test_cpsat_deterministic_reproducibility():
    cmd = {
        "commandId": "cmd-cp-det",
        "jobId": "job-cp-det",
        "attemptId": "att-1",
        "timeLimitSeconds": 10,
        "compartment": {
            "compartmentId": "comp-1",
            "lengthMm": 4000,
            "widthMm": 2000,
            "heightMm": 2000,
            "maxPayloadKg": 5000.0
        },
        "items": [
            {
                "cargoItemId": "item-1",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 150.0,
                "stackable": True,
                "quantity": 3
            }
        ]
    }

    res1 = solve_cpsat_cargo(cmd, random_seed=42, force_cpsat=True)
    res2 = solve_cpsat_cargo(cmd, random_seed=42, force_cpsat=True)

    assert res1["contentDigest"] == res2["contentDigest"]
    assert res1["placements"] == res2["placements"]

def test_cpsat_rotatable_orientation_handling():
    cmd = {
        "commandId": "cmd-cp-rot",
        "jobId": "job-cp-rot",
        "attemptId": "att-1",
        "timeLimitSeconds": 10,
        "compartment": {
            "compartmentId": "comp-narrow",
            "lengthMm": 1200,
            "widthMm": 800, # Width is 800mm
            "heightMm": 1000,
            "maxPayloadKg": 5000.0
        },
        "items": [
            {
                "cargoItemId": "long-box",
                "lengthMm": 1000, # 1000mm length, 600mm width
                "widthMm": 600,
                "heightMm": 500,
                "weightKg": 100.0,
                "permittedOrientations": ["DEFAULT", "ROTATE_YAW_90"],
                "quantity": 1
            }
        ]
    }

    res = solve_cpsat_cargo(cmd, force_cpsat=True)
    assert len(res["placements"]) == 1
    p = res["placements"][0]
    assert p["xMm"] + p["lengthMm"] <= 1200
    assert p["yMm"] + p["widthMm"] <= 800

def test_cpsat_payload_capacity_enforcement():
    cmd = {
        "commandId": "cmd-cp-cap",
        "jobId": "job-cp-cap",
        "attemptId": "att-1",
        "timeLimitSeconds": 10,
        "compartment": {
            "compartmentId": "comp-small",
            "lengthMm": 5000,
            "widthMm": 2000,
            "heightMm": 2000,
            "maxPayloadKg": 300.0 # Only 300 kg capacity
        },
        "items": [
            {
                "cargoItemId": "heavy-box",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 200.0,
                "quantity": 2 # 400 kg total
            }
        ]
    }

    res = solve_cpsat_cargo(cmd, force_cpsat=True)
    # Only 1 item can be placed within 300kg payload limit
    assert len(res["placements"]) == 1
    assert len(res["unassignedItems"]) == 1
    total_placed_w = sum(p["weightKg"] for p in res["placements"])
    assert total_placed_w <= 300.0

def test_cpsat_hazard_forbidden_co_loading():
    cmd = {
        "commandId": "cmd-cp-forbid",
        "jobId": "job-cp-forbid",
        "attemptId": "att-1",
        "timeLimitSeconds": 10,
        "compartment": {
            "compartmentId": "comp-1",
            "lengthMm": 5000,
            "widthMm": 2000,
            "heightMm": 2000,
            "maxPayloadKg": 5000.0
        },
        "ruleSnapshot": {
            "rules": [
                {
                    "ruleId": "r1",
                    "ruleCode": "FORBID-1-2.1",
                    "primaryHazardClass": "1",
                    "secondaryHazardClass": "2.1",
                    "segregationType": "FORBIDDEN"
                }
            ]
        },
        "items": [
            {
                "cargoItemId": "haz-1",
                "hazardClass": "1",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 100.0,
                "quantity": 1
            },
            {
                "cargoItemId": "haz-2.1",
                "hazardClass": "2.1",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 100.0,
                "quantity": 1
            }
        ]
    }

    res = solve_cpsat_cargo(cmd, force_cpsat=True)
    # Cannot co-load both Class 1 and Class 2.1
    assert len(res["placements"]) == 1
    assert len(res["unassignedItems"]) == 1

def test_cpsat_hazard_separation_distance():
    cmd = {
        "commandId": "cmd-cp-sep",
        "jobId": "job-cp-sep",
        "attemptId": "att-1",
        "timeLimitSeconds": 10,
        "compartment": {
            "compartmentId": "comp-1",
            "lengthMm": 12000,
            "widthMm": 2400,
            "heightMm": 2600,
            "maxPayloadKg": 25000.0
        },
        "ruleSnapshot": {
            "rules": [
                {
                    "ruleId": "r-sep",
                    "ruleCode": "SEP-1-3",
                    "primaryHazardClass": "1",
                    "secondaryHazardClass": "3",
                    "segregationType": "SEPARATED_BY_DISTANCE",
                    "minDistanceMm": 3000
                }
            ]
        },
        "items": [
            {
                "cargoItemId": "haz-1",
                "hazardClass": "1",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 100.0,
                "quantity": 1
            },
            {
                "cargoItemId": "haz-3",
                "hazardClass": "3",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 100.0,
                "quantity": 1
            }
        ]
    }

    res = solve_cpsat_cargo(cmd, force_cpsat=True)
    assert len(res["placements"]) == 2
    p1 = res["placements"][0]
    p2 = res["placements"][1]
    c1 = (p1["xMm"] + p1["lengthMm"]/2, p1["yMm"] + p1["widthMm"]/2, p1["zMm"] + p1["heightMm"]/2)
    c2 = (p2["xMm"] + p2["lengthMm"]/2, p2["yMm"] + p2["widthMm"]/2, p2["zMm"] + p2["heightMm"]/2)
    dist = ((c1[0]-c2[0])**2 + (c1[1]-c2[1])**2 + (c1[2]-c2[2])**2)**0.5
    assert dist >= 3000

def test_cpsat_stacking_fail_closed_on_unstackable():
    cmd = {
        "commandId": "cmd-cp-stack",
        "jobId": "job-cp-stack",
        "attemptId": "att-1",
        "timeLimitSeconds": 10,
        "compartment": {
            "compartmentId": "comp-narrow",
            "lengthMm": 1000,
            "widthMm": 1000,
            "heightMm": 2500,
            "maxPayloadKg": 5000.0
        },
        "items": [
            {
                "cargoItemId": "fragile-1",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 100.0,
                "stackable": False, # Non-stackable!
                "quantity": 1
            },
            {
                "cargoItemId": "fragile-2",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 100.0,
                "stackable": False, # Non-stackable!
                "quantity": 1
            }
        ]
    }

    res = solve_cpsat_cargo(cmd, force_cpsat=True)
    # Since floor can only fit 1 item (1000x1000) and both are non-stackable, only 1 item can be placed
    assert len(res["placements"]) == 1
    assert len(res["unassignedItems"]) == 1

def test_cpsat_preserves_heuristic_incumbent_on_timeout():
    # Model with short limit to simulate bounded search / incumbent preservation
    cmd = {
        "commandId": "cmd-timeout",
        "jobId": "job-timeout",
        "attemptId": "att-1",
        "timeLimitSeconds": 0.001,
        "compartment": {
            "compartmentId": "comp-1",
            "lengthMm": 12000,
            "widthMm": 2400,
            "heightMm": 2600,
            "maxPayloadKg": 25000.0
        },
        "items": [
            {
                "cargoItemId": "box-1",
                "lengthMm": 1000,
                "widthMm": 800,
                "heightMm": 600,
                "weightKg": 100.0,
                "quantity": 2
            }
        ]
    }

    res = solve_cpsat_cargo(cmd)
    # Must preserve valid candidate even when CP-SAT search concludes or times out
    assert res["solverStatus"] in ("OPTIMAL", "FEASIBLE", "UNKNOWN")
    assert len(res["placements"]) > 0

def test_cpsat_proven_optimal_partial_placement():
    # 3 units requested, only 2 physically fit in 2000x1000 compartment
    cmd = {
        "commandId": "cmd-partial-opt",
        "jobId": "job-partial-opt",
        "attemptId": "att-1",
        "timeLimitSeconds": 10,
        "compartment": {
            "compartmentId": "comp-part",
            "lengthMm": 2000,
            "widthMm": 1000,
            "heightMm": 1000,
            "maxPayloadKg": 5000.0
        },
        "items": [
            {"cargoItemId": "box-1", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1},
            {"cargoItemId": "box-2", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1},
            {"cargoItemId": "box-3", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1}
        ]
    }
    res = solve_cpsat_cargo(cmd, force_cpsat=True)
    # CP-SAT proves that 2 is the maximum possible placement, so solverStatus is OPTIMAL
    assert res["solverStatus"] == "OPTIMAL"
    assert len(res["placements"]) == 2
    assert len(res["unassignedItems"]) == 1
    assert res["unassignedItems"][0]["reasonCode"] == "OPTIMIZATION_BOUND_OR_CAPACITY"
    assert res["diagnostics"]["modelOptimalityProven"] is True

def test_cpsat_diagonal_euclidean_hazard_segregation():
    # Compartment 2200 x 2200. Min separation distance 1500 mm.
    # Axis-aligned projection alone would require 1000 + 1500 + 1000 = 3500 mm (would fit only 1).
    # But diagonal placement center distance is sqrt((1200)^2 + (1200)^2) = 1697 mm >= 1500 mm.
    # True 3D Euclidean constraint allows both items to be co-loaded safely.
    cmd = {
        "commandId": "cmd-diag",
        "jobId": "job-diag",
        "attemptId": "att-1",
        "timeLimitSeconds": 10,
        "compartment": {
            "compartmentId": "comp-diag",
            "lengthMm": 2200,
            "widthMm": 2200,
            "heightMm": 1000,
            "maxPayloadKg": 5000.0
        },
        "ruleSnapshot": {
            "rules": [
                {
                    "ruleId": "r-sep",
                    "ruleCode": "SEP-3-8",
                    "primaryHazardClass": "3",
                    "secondaryHazardClass": "8",
                    "segregationType": "SEPARATED_BY_DISTANCE",
                    "minDistanceMm": 1500
                }
            ]
        },
        "items": [
            {"cargoItemId": "flam", "hazardClass": "3", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1},
            {"cargoItemId": "corr", "hazardClass": "8", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 1}
        ]
    }
    res = solve_cpsat_cargo(cmd, force_cpsat=True)
    assert len(res["placements"]) == 2
    # Verify strict revalidation passes with 3D Euclidean distance
    p1 = res["placements"][0]
    p2 = res["placements"][1]
    c1 = (p1["xMm"] + p1["lengthMm"]/2, p1["yMm"] + p1["widthMm"]/2, p1["zMm"] + p1["heightMm"]/2)
    c2 = (p2["xMm"] + p2["lengthMm"]/2, p2["yMm"] + p2["widthMm"]/2, p2["zMm"] + p2["heightMm"]/2)
    dist = ((c1[0]-c2[0])**2 + (c1[1]-c2[1])**2 + (c1[2]-c2[2])**2)**0.5
    assert dist >= 1500

