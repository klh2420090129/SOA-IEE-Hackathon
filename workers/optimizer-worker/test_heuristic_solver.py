import pytest
from heuristic_solver import solve_3d_packing, compute_content_digest, ORIENTATION_DEFAULT, ORIENTATION_ROTATE_YAW_90

def test_deterministic_output_for_identical_input():
    cmd = {
        "commandId": "cmd-1",
        "jobId": "job-1",
        "attemptId": "att-1",
        "timeLimitSeconds": 20,
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
                "weightKg": 200.0,
                "stackable": True,
                "maxStackWeightKg": 500.0,
                "quantity": 2
            }
        ]
    }

    res1 = solve_3d_packing(cmd)
    res2 = solve_3d_packing(cmd)

    assert res1["solverStatus"] == "FEASIBLE"
    assert res1["contentDigest"] == res2["contentDigest"]
    assert len(res1["placements"]) == 2
    assert res1["placements"] == res2["placements"]

def test_unknown_outcome_when_no_items_can_be_placed():
    cmd = {
        "commandId": "cmd-oversized",
        "jobId": "job-oversized",
        "attemptId": "att-1",
        "timeLimitSeconds": 20,
        "compartment": {
            "compartmentId": "comp-1",
            "lengthMm": 2000,
            "widthMm": 2000,
            "heightMm": 2000,
            "maxPayloadKg": 1000.0
        },
        "items": [
            {
                "cargoItemId": "oversized-1",
                "lengthMm": 5000,
                "widthMm": 5000,
                "heightMm": 5000,
                "weightKg": 500.0,
                "stackable": True,
                "maxStackWeightKg": 500.0,
                "quantity": 1
            }
        ]
    }

    res = solve_3d_packing(cmd)
    assert res["solverStatus"] == "UNKNOWN"
    assert len(res["placements"]) == 0
    assert len(res["unassignedItems"]) == 1

def test_fail_closed_on_unknown_stacking_capability():
    cmd = {
        "commandId": "cmd-stack",
        "jobId": "job-stack",
        "attemptId": "att-1",
        "timeLimitSeconds": 20,
        "compartment": {
            "compartmentId": "comp-narrow",
            "lengthMm": 1000,
            "widthMm": 1000,
            "heightMm": 2600,
            "maxPayloadKg": 5000.0
        },
        "items": [
            {
                "cargoItemId": "base-unknown",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 200.0,
                "stackable": None,  # Unknown capability
                "maxStackWeightKg": None,
                "quantity": 1
            },
            {
                "cargoItemId": "upper-box",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 200.0,
                "stackable": True,
                "maxStackWeightKg": 500.0,
                "quantity": 1
            }
        ]
    }

    res = solve_3d_packing(cmd)
    # The base should be placed at floor (0,0,0). Upper item cannot stack on unknown base, so it remains unassigned.
    assert len(res["placements"]) == 1
    assert res["placements"][0]["cargoItemId"] == "base-unknown"
    assert len(res["unassignedItems"]) == 1
    assert res["unassignedItems"][0]["cargoItemId"] == "upper-box"

def test_hazard_segregation_distance_enforced():
    cmd = {
        "commandId": "cmd-haz",
        "jobId": "job-haz",
        "attemptId": "att-1",
        "timeLimitSeconds": 20,
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
                    "ruleId": "r1",
                    "ruleCode": "CMV-HAZ-1-3",
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
                "weightKg": 200.0,
                "stackable": True,
                "quantity": 1
            },
            {
                "cargoItemId": "haz-3",
                "hazardClass": "3",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 200.0,
                "stackable": True,
                "quantity": 1
            }
        ]
    }

    res = solve_3d_packing(cmd)
    assert len(res["placements"]) == 2
    p1 = res["placements"][0]
    p2 = res["placements"][1]
    # Check 3D center-to-center distance
    c1 = (p1["xMm"] + p1["lengthMm"]/2, p1["yMm"] + p1["widthMm"]/2, p1["zMm"] + p1["heightMm"]/2)
    c2 = (p2["xMm"] + p2["lengthMm"]/2, p2["yMm"] + p2["widthMm"]/2, p2["zMm"] + p2["heightMm"]/2)
    dist = ((c1[0]-c2[0])**2 + (c1[1]-c2[1])**2 + (c1[2]-c2[2])**2)**0.5
    assert dist >= 3000

def test_hazard_forbidden_co_loading_rejects_second_item():
    cmd = {
        "commandId": "cmd-forbid",
        "jobId": "job-forbid",
        "attemptId": "att-1",
        "timeLimitSeconds": 20,
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
                    "ruleId": "r2",
                    "ruleCode": "CMV-HAZ-1-2.1",
                    "primaryHazardClass": "1",
                    "secondaryHazardClass": "2.1",
                    "segregationType": "FORBIDDEN",
                    "minDistanceMm": 0
                }
            ]
        },
        "items": [
            {
                "cargoItemId": "item-haz1",
                "hazardClass": "1",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 200.0,
                "stackable": True,
                "maxStackWeightKg": 500.0,
                "quantity": 1
            },
            {
                "cargoItemId": "item-haz21",
                "hazardClass": "2.1",
                "lengthMm": 1000,
                "widthMm": 1000,
                "heightMm": 1000,
                "weightKg": 200.0,
                "stackable": True,
                "maxStackWeightKg": 500.0,
                "quantity": 1
            }
        ]
    }

    res = solve_3d_packing(cmd)
    assert len(res["placements"]) == 1
    assert len(res["unassignedItems"]) == 1
    assert res["unassignedItems"][0]["reasonCode"] == "HAZARD_COLOAD_FORBIDDEN"

def test_cross_language_candidate_digest_parity():
    # Test 1: Empty placements
    assert compute_content_digest([]) == "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945"

    # Test 2: Single placement matching Java CandidateDigestUtilsTest
    p1 = {
        "placementId": "pl-1",
        "cargoItemId": "item-1",
        "instanceIndex": 0,
        "hazardClass": "3",
        "unNumber": "UN1993",
        "xMm": 0,
        "yMm": 0,
        "zMm": 0,
        "lengthMm": 1200,
        "widthMm": 800,
        "heightMm": 1000,
        "weightKg": 500.0,
        "orientation": "DEFAULT",
        "stackable": True,
        "maxStackWeightKg": 1500.0
    }
    assert compute_content_digest([p1]) == "e45ded0b55dd2051095602375f842716d8dabe20955bb9d26e3429a45d44690f"

    # Test 3: Multiple placements matching Java CandidateDigestUtilsTest
    p2 = {
        "placementId": "pl-2",
        "cargoItemId": "item-2",
        "instanceIndex": 1,
        "hazardClass": "8",
        "unNumber": "UN1789",
        "xMm": 1500,
        "yMm": 500,
        "zMm": 0,
        "lengthMm": 1000,
        "widthMm": 1000,
        "heightMm": 800,
        "weightKg": 350.25,
        "orientation": "ROTATE_YAW_90",
        "stackable": False,
        "maxStackWeightKg": None
    }
    assert compute_content_digest([p1, p2]) == "c37d14aad45e2ad525a7761a4f5b2dc4c25ded8ba3c612bf51cd1ba68e69cb4d"

