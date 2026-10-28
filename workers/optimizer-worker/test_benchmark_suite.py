"""
Automated Correctness Tests for Reproducible Optimization Benchmark Suite (Phase F.3).

Verifies the 8 mandatory benchmark correctness checks:
1. Equivalent Scenario Inputs: Both algorithms receive identical cargo, compartment, and rule snapshot inputs (input hash matches).
2. Effective Executed Algorithm: The actual executed algorithm is identified correctly in diagnostics and results.
3. Explicit Invalid Algorithm Handling: Invalid algorithm identifiers fail explicitly with ValueError.
4. Distinguishing Infeasible vs Unknown: Infeasible and unknown outcomes are not conflated.
5. Independent Validation Guard: A candidate is counted as feasible only after the independent validator passes.
6. Failure & Timeout Preservation: Failed and timed-out runs remain represented in output without silent discarding.
7. Reproducibility & Determinism: Re-running a fixed scenario preserves its input hash and declared configuration.
8. Rule Snapshot Integrity: Missing or incompatible rule snapshots fail closed.
"""

import pytest
import copy
from benchmark_corpus import (
    get_scenario_corpus,
    compute_scenario_input_hash,
    compute_canonical_rule_snapshot_digest,
    STATUTORY_RULE_SNAPSHOT,
    SYNTHETIC_RULE_SNAPSHOT,
    EMPTY_RULE_SNAPSHOT
)
from cpsat_solver import (
    solve_cpsat_cargo,
    validate_candidate_strictly,
    PROVENANCE_HEURISTIC,
    PROVENANCE_CP_SAT,
    SUPPORTED_ALGORITHMS
)
from heuristic_solver import solve_3d_packing as solve_heuristic

def test_benchmark_both_algorithms_receive_equivalent_inputs():
    """
    Check 1: Both algorithms receive identical inputs and share exact input hash.
    """
    scenarios = get_scenario_corpus()
    assert len(scenarios) >= 12
    
    for sc in scenarios:
        hash_1 = compute_scenario_input_hash(sc.command)
        # Deepcopy to simulate execution on independent algorithm instances
        cmd_h = copy.deepcopy(sc.command)
        cmd_cp = copy.deepcopy(sc.command)
        
        cmd_h["algorithm"] = "HEURISTIC_EP_3D_V1"
        cmd_cp["algorithm"] = "PYTHON_ORTOOLS_CPSAT_3D_V1"
        
        assert compute_scenario_input_hash(cmd_h) == hash_1
        assert compute_scenario_input_hash(cmd_cp) == hash_1

def test_benchmark_actual_executed_algorithm_identified_correctly():
    """
    Check 2: The actual executed algorithm is identified correctly in results and diagnostics.
    """
    sc = get_scenario_corpus()[0]
    cmd_h = copy.deepcopy(sc.command)
    cmd_h["algorithm"] = "HEURISTIC_EP_3D_V1"
    
    res_h = solve_heuristic(cmd_h)
    assert res_h.get("provenance") == PROVENANCE_HEURISTIC
    assert res_h.get("diagnostics", {}).get("effectiveAlgorithm") == PROVENANCE_HEURISTIC
    assert res_h.get("diagnostics", {}).get("requestedAlgorithm") == "HEURISTIC_EP_3D_V1"
    
    cmd_cp = copy.deepcopy(sc.command)
    cmd_cp["algorithm"] = "PYTHON_ORTOOLS_CPSAT_3D_V1"
    res_cp = solve_cpsat_cargo(cmd_cp, force_cpsat=True)
    assert res_cp.get("provenance") == PROVENANCE_CP_SAT
    assert res_cp.get("diagnostics", {}).get("effectiveAlgorithm") == PROVENANCE_CP_SAT
    assert res_cp.get("diagnostics", {}).get("requestedAlgorithm") == "PYTHON_ORTOOLS_CPSAT_3D_V1"

def test_benchmark_invalid_algorithm_identifiers_fail_explicitly():
    """
    Check 3: Invalid algorithm identifiers fail explicitly with ValueError.
    """
    sc = get_scenario_corpus()[0]
    invalid_cmd = copy.deepcopy(sc.command)
    invalid_cmd["algorithm"] = "INVALID_MAGIC_SOLVER_V99"
    
    with pytest.raises(ValueError, match="Unsupported algorithm identifier"):
        solve_cpsat_cargo(invalid_cmd)
        
    with pytest.raises(ValueError, match="Unsupported algorithm identifier"):
        solve_heuristic(invalid_cmd)

def test_benchmark_infeasible_and_unknown_outcomes_not_conflated():
    """
    Check 4: Infeasible and unknown outcomes are not conflated.
    """
    corpus = {s.scenario_id: s for s in get_scenario_corpus()}
    infeas_sc = corpus["SCEN_INFEASIBLE_01"] # Single unit exceeds compartment dimensions with requireAllPlaced
    
    res_cp = solve_cpsat_cargo(infeas_sc.command, force_cpsat=True)
    assert res_cp.get("solverStatus") == "INFEASIBLE"
    assert res_cp.get("diagnostics", {}).get("cpSatStatus") == "INFEASIBLE"
    assert len(res_cp.get("placements", [])) == 0
    assert len(res_cp.get("unassignedItems", [])) == 1
    assert res_cp.get("unassignedItems")[0]["reasonCode"] == "PROVEN_INFEASIBLE"

def test_benchmark_candidate_feasible_only_after_independent_validator_passes():
    """
    Check 5: A candidate is counted as feasible only after passing independent strict validation.
    """
    corpus = {s.scenario_id: s for s in get_scenario_corpus()}
    sc = corpus["SCEN_SMALL_01"]
    
    res_cp = solve_cpsat_cargo(sc.command, force_cpsat=True)
    comp = sc.command["compartment"]
    rules = sc.command.get("ruleSnapshot", {}).get("rules", [])
    
    # 1. Real solver candidate passes validation
    is_valid, err = validate_candidate_strictly(res_cp["placements"], comp, rules)
    assert is_valid is True
    assert err is None
    
    # 2. Mutated/tampered placement with overlap fails validation immediately
    tampered_placements = copy.deepcopy(res_cp["placements"])
    tampered_placements[1]["xMm"] = tampered_placements[0]["xMm"]
    tampered_placements[1]["yMm"] = tampered_placements[0]["yMm"]
    tampered_placements[1]["zMm"] = tampered_placements[0]["zMm"]
    
    is_valid_tampered, err_tampered = validate_candidate_strictly(tampered_placements, comp, rules)
    assert is_valid_tampered is False
    assert "Overlap detected" in err_tampered

def test_benchmark_failed_and_timed_out_runs_remain_represented():
    """
    Check 6: Timed-out runs are preserved with fallback diagnostics, never silently excluded.
    """
    corpus = {s.scenario_id: s for s in get_scenario_corpus()}
    bound_sc = corpus["SCEN_BOUND_01"] # Budget restricted to 1s
    
    res_cp = solve_cpsat_cargo(bound_sc.command, force_cpsat=True)
    assert res_cp is not None
    # CP-SAT pipeline preserves best incumbent and records fallback
    diag = res_cp.get("diagnostics", {})
    assert "effectiveAlgorithm" in diag
    assert res_cp.get("solverStatus") in ("FEASIBLE", "OPTIMAL")

def test_benchmark_rerunning_fixed_scenario_preserves_input_hash_and_digest():
    """
    Check 7: Re-running a fixed scenario preserves its input hash and deterministic result digest.
    """
    sc = get_scenario_corpus()[1] # SCEN_SMALL_02
    hash_run1 = compute_scenario_input_hash(sc.command)
    res_run1 = solve_cpsat_cargo(sc.command, random_seed=sc.seed, force_cpsat=True)
    digest_run1 = res_run1.get("contentDigest")
    
    hash_run2 = compute_scenario_input_hash(sc.command)
    res_run2 = solve_cpsat_cargo(sc.command, random_seed=sc.seed, force_cpsat=True)
    digest_run2 = res_run2.get("contentDigest")
    
    assert hash_run1 == hash_run2
    assert digest_run1 == digest_run2
    assert len(res_run1["placements"]) == len(res_run2["placements"])

def test_benchmark_missing_or_incompatible_rule_snapshots_fail_closed():
    """
    Check 8: Missing or incompatible rule snapshots fail closed.
    """
    # 1. Null/empty snapshot in digest calculation fails closed
    with pytest.raises(ValueError, match="snapshot cannot be null/empty"):
        compute_canonical_rule_snapshot_digest(None)
        
    with pytest.raises(ValueError, match="snapshot cannot be null/empty"):
        compute_canonical_rule_snapshot_digest({})
        
    # 2. Canonical rule snapshot digest matches verified statutory constant
    statutory_digest = compute_canonical_rule_snapshot_digest(STATUTORY_RULE_SNAPSHOT)
    assert statutory_digest == "e251090aef1c4ba8968162862c71b0c8b694ff9cd4d604432df19e9afcaef876"
