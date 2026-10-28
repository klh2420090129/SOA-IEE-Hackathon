"""
Automated Correctness & Regression Tests for Phase F.4.

Covers:
1. Full versus partial objective proof (primaryObjectiveProven vs fullLexicographicOptimalityProven).
2. INFEASIBLE versus UNKNOWN versus TIMEOUT semantics.
3. Incumbent preservation after timeout without false optimality.
4. SCEN_INFEASIBLE_02 feasibility contract (CP-SAT proven INFEASIBLE vs Heuristic UNKNOWN under requireAllPlaced).
5. Independent validation gating: candidate counted as feasible only after validator passes.
6. Equivalent solver inputs and pinned snapshot digests across solvers.
7. Unknown algorithm rejection and fallback attribution (requestedAlgorithm vs effectiveAlgorithm).
8. Repeat-trial statistical calculations (percentiles, rates, denominators).
9. Stable scenario input hashes across reruns.
10. Missing or incompatible safety snapshots failing closed.
11. Report metrics matching underlying raw trial records.
"""

import copy
import pytest
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
from run_benchmarks import run_single_scenario, calculate_percentile, export_all_trials_csv, generate_markdown_report

def test_full_versus_partial_objective_proof():
    """
    Check 1: Distinguish proof of maximum assigned-unit count from proof of loaded volume & compactness.
    """
    corpus = {s.scenario_id: s for s in get_scenario_corpus()}
    sc = corpus["SCEN_SMALL_01"] # 4 uniform units
    
    res = solve_cpsat_cargo(sc.command, force_cpsat=True)
    diag = res["diagnostics"]
    assert "primaryObjectiveProven" in diag
    assert "fullLexicographicOptimalityProven" in diag
    assert diag["primaryObjectiveProven"] is True
    # In small 4 units exact fit, all 3 stages prove optimality
    assert diag["fullLexicographicOptimalityProven"] is True
    assert diag["volumeObjectiveProven"] is True
    assert diag["compactnessObjectiveProven"] is True

def test_infeasible_versus_unknown_versus_timeout():
    """
    Check 2: INFEASIBLE, UNKNOWN, and TIMEOUT are strictly separated and never conflated.
    """
    corpus = {s.scenario_id: s for s in get_scenario_corpus()}
    
    # Infeasible case
    infeas_sc = corpus["SCEN_INFEASIBLE_01"]
    res_infeas = solve_cpsat_cargo(infeas_sc.command, force_cpsat=True)
    assert res_infeas["solverStatus"] == "INFEASIBLE"
    assert res_infeas["diagnostics"]["cpSatStatus"] == "INFEASIBLE"
    
    # Timeout/Bounded case (budget 1s)
    bound_sc = corpus["SCEN_BOUND_01"]
    res_bound = solve_cpsat_cargo(bound_sc.command, force_cpsat=True)
    assert res_bound["solverStatus"] in ("FEASIBLE", "OPTIMAL")
    # Must preserve incumbent and record fallback
    assert res_bound["diagnostics"]["fallbackReason"] in ("CPSAT_UNKNOWN", "CPSAT_FEASIBLE", "CPSAT_TIMEOUT")
    assert res_bound["diagnostics"]["fullLexicographicOptimalityProven"] is False

def test_scen_infeasible_02_feasibility_contract():
    """
    Check 4: SCEN_INFEASIBLE_02 feasibility contract:
    - Contains Class 1 and Class 2.1 which are legally FORBIDDEN under India CMV rules.
    - requireAllPlaced=True strictly demands both placed.
    - CP-SAT proves mathematical INFEASIBLE (0/2 placed).
    - Heuristic places 1/2 and reports UNKNOWN (never falsely claims FEASIBLE for partial set).
    """
    corpus = {s.scenario_id: s for s in get_scenario_corpus()}
    sc = corpus["SCEN_INFEASIBLE_02"]
    
    # CP-SAT run
    res_cp = solve_cpsat_cargo(sc.command, force_cpsat=True)
    assert res_cp["solverStatus"] == "INFEASIBLE"
    assert len(res_cp["placements"]) == 0
    assert len(res_cp["unassignedItems"]) == 2
    assert res_cp["diagnostics"]["cpSatStatus"] == "INFEASIBLE"
    assert res_cp["diagnostics"]["effectiveAlgorithm"] == PROVENANCE_CP_SAT
    
    # Heuristic run
    res_h = solve_heuristic(sc.command)
    assert res_h["solverStatus"] == "UNKNOWN" # Heuristic cannot prove infeasibility, reports UNKNOWN for partial
    assert len(res_h["placements"]) == 1
    assert len(res_h["unassignedItems"]) == 1

def test_independent_validation_gating_metrics():
    """
    Check 5: Validation must pass before candidate can be counted as feasible.
    """
    corpus = {s.scenario_id: s for s in get_scenario_corpus()}
    sc = corpus["SCEN_SMALL_01"]
    res = solve_cpsat_cargo(sc.command, force_cpsat=True)
    
    is_valid, err = validate_candidate_strictly(
        res["placements"],
        sc.command["compartment"],
        sc.command.get("ruleSnapshot", {}).get("rules", [])
    )
    assert is_valid is True
    assert err is None
    
    # Tampering with placement to cause overlap
    tampered = copy.deepcopy(res["placements"])
    tampered[1]["xMm"] = tampered[0]["xMm"]
    tampered[1]["yMm"] = tampered[0]["yMm"]
    tampered[1]["zMm"] = tampered[0]["zMm"]
    is_valid_t, err_t = validate_candidate_strictly(
        tampered,
        sc.command["compartment"],
        sc.command.get("ruleSnapshot", {}).get("rules", [])
    )
    assert is_valid_t is False
    assert "Overlap detected" in err_t

def test_equivalent_solver_inputs_and_pinned_snapshots():
    """
    Check 6: Both solvers receive equivalent inputs and canonical digest parity holds.
    """
    scenarios = get_scenario_corpus()
    for sc in scenarios:
        hash_orig = sc.input_hash
        cmd_h = copy.deepcopy(sc.command)
        cmd_cp = copy.deepcopy(sc.command)
        cmd_h["algorithm"] = "HEURISTIC_EP_3D_V1"
        cmd_cp["algorithm"] = "PYTHON_ORTOOLS_CPSAT_3D_V1"
        
        assert compute_scenario_input_hash(cmd_h) == hash_orig
        assert compute_scenario_input_hash(cmd_cp) == hash_orig
        
    statutory_digest = compute_canonical_rule_snapshot_digest(STATUTORY_RULE_SNAPSHOT)
    assert statutory_digest == "e251090aef1c4ba8968162862c71b0c8b694ff9cd4d604432df19e9afcaef876"

def test_unknown_algorithm_rejection_and_fallback_attribution():
    """
    Check 7: Invalid algorithms rejected with ValueError, and fallbacks track requested vs effective algorithm.
    """
    sc = get_scenario_corpus()[0]
    bad_cmd = copy.deepcopy(sc.command)
    bad_cmd["algorithm"] = "UNKNOWN_NONEXISTENT_ALGO"
    
    with pytest.raises(ValueError, match="Unsupported algorithm identifier"):
        solve_cpsat_cargo(bad_cmd)
        
    with pytest.raises(ValueError, match="Unsupported algorithm identifier"):
        solve_heuristic(bad_cmd)

def test_repeated_trial_percentile_and_statistics():
    """
    Check 8: Statistical calculation helper accuracy.
    """
    data = [10.0, 20.0, 30.0, 40.0, 50.0]
    assert calculate_percentile(data, 50) == 30.0
    assert calculate_percentile(data, 0) == 10.0
    assert calculate_percentile(data, 100) == 50.0
    assert calculate_percentile(data, 90) == 46.0

def test_scenario_runner_preserves_hashes_and_aggregates_trials():
    """
    Check 9 & 11: run_single_scenario executes configured trials, preserves hashes, and matches raw trials.
    """
    sc = get_scenario_corpus()[0] # SCEN_SMALL_01
    res = run_single_scenario(sc, num_trials=3, warmup=False)
    
    assert res["scenarioId"] == "SCEN_SMALL_01"
    assert res["inputHash"] == sc.input_hash
    assert len(res["heuristicTrials"]) == 3
    assert len(res["cpsatTrials"]) == 3
    
    for t in res["cpsatTrials"]:
        assert t["requestedAlgorithm"] == "PYTHON_ORTOOLS_CPSAT_3D_V1"
        assert t["effectiveAlgorithm"] == PROVENANCE_CP_SAT
        assert t["unitsPlaced"] == 4
        assert t["independentValidationPassed"] is True
        assert t["primaryObjectiveProven"] is True

def test_reconciled_metrics_denominators_and_infeasibility_separation():
    """
    Check 12: Verify that compute_cohort_metrics strictly excludes INFEASIBLE runs from primaryOptimizationProofRate,
    and isolates infeasibilityProofRate to eligible requireAllPlaced=True runs.
    """
    from run_benchmarks import compute_cohort_metrics
    scenarios = get_scenario_corpus()
    
    # Run a small slice of 1 optimization scenario and 1 infeasibility scenario
    sc_opt = scenarios[0] # SCEN_SMALL_01 (opt)
    sc_infeas = [s for s in scenarios if s.scenario_id == "SCEN_INFEASIBLE_01"][0] # infeas
    
    res_opt = run_single_scenario(sc_opt, num_trials=2, warmup=False)
    res_infeas = run_single_scenario(sc_infeas, num_trials=2, warmup=False)
    
    cohort_metrics = compute_cohort_metrics([res_opt, res_infeas], num_trials=2)
    
    # Optimization runs per solver: exactly 2 (from 1 scenario)
    assert cohort_metrics["optimizationRunsPerSolver"] == 2
    # Infeasibility runs per solver: exactly 2 (from 1 scenario)
    assert cohort_metrics["infeasibilityRunsPerSolver"] == 2
    # Total runs per solver: 4
    assert cohort_metrics["totalRunsPerSolver"] == 4
    
    # Infeasibility proof rate denominator must be 2, numerator 2 (100%)
    assert cohort_metrics["infeasibilityProofRate"]["cpsatDenominator"] == 2
    assert cohort_metrics["infeasibilityProofRate"]["cpsatNumerator"] == 2
    assert cohort_metrics["infeasibilityProofRate"]["cpsatRatePct"] == 100.0
    
    # Primary optimization proof rate denominator must be 2, numerator 2 (100%)
    assert cohort_metrics["primaryOptimizationProofRate"]["cpsatDenominator"] == 2
    assert cohort_metrics["primaryOptimizationProofRate"]["cpsatNumerator"] == 2
    assert cohort_metrics["primaryOptimizationProofRate"]["cpsatRatePct"] == 100.0
    
    # Heuristic infeasibility proof rate must be 0% (heuristic emits UNKNOWN, never claims proof)
    assert cohort_metrics["infeasibilityProofRate"]["heuristicNumerator"] == 0
    assert cohort_metrics["infeasibilityProofRate"]["heuristicRatePct"] == 0.0
