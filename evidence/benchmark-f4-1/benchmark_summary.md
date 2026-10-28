# Phase F.4.1 — Final Reconciled Benchmark Metrics & Integrity Report

## 1. Experimental Environment & Provenance Metadata
- **Host Platform**: `Windows 11 (AMD64)`
- **Runtime**: `Python 3.12.10`, `Google OR-Tools 9.10.4067`, `OpenJDK 21.0.6`
- **Total Scenarios**: `12` (10 optimization instances, 2 strict infeasibility instances)
- **Measured Trials per Scenario**: `3` (each trial executed with deterministic seed offsets)
- **Warm-up**: 1 unmeasured execution per scenario executed prior to trial recording.

## 2. Metric Definitions, Denominators & Inclusion Rules
To prevent conflation between infeasibility proofs and optimization proofs, denominators are strictly partitioned:
1. **Infeasibility Proof Rate**: $\frac{\text{Runs proving mathematical INFEASIBLE}}{\text{Eligible infeasibility-test runs with } requireAllPlaced=True}$.
2. **Primary Optimization Proof Rate**: $\frac{\text{Eligible optimization runs proving max placed count}}{\text{Eligible optimization runs with } requireAllPlaced=False}$. *Note: INFEASIBLE results are excluded from this numerator and denominator.*
3. **Full Lexicographic Proof Rate**: $\frac{\text{Eligible optimization runs proving Count + Volume + Compactness}}{\text{Eligible optimization runs with } requireAllPlaced=False}$.
4. **Complete Feasibility Rate**: $\frac{\text{Runs satisfying the scenario's complete placement and safety contract}}{\text{Total trial runs}}$.
5. **Candidate Validation Pass Rate**: $\frac{\text{Returned candidate layouts passing independent 3D physics & safety validation}}{\text{Returned candidate layouts with units placed } > 0}$.
6. **Fallback Rate**: $\frac{\text{CP-SAT runs falling back to heuristic incumbent}}{\text{Total CP-SAT trial runs}}$.

## 3. Reconciled Per-Scenario Results (Across Repeated Trials)

| Scenario ID | Size | Units | Contract | Algorithm | Effective Executed | Status | Placed (avg) | Vol ($m^3$) | Latency (med / p90 / max) | Valid? | Primary Proof | Full Lex Proof | Infeas Proof | Fallback |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `SCEN_SMALL_01` | SMALL | 4 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 4.0/4 | 4.0 | 0.13 / 0.14 / 0.14ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **4.0/4** | **4.0** | **21.08 / 21.31 / 21.37ms** | True | **100.0%** | **100.0%** | **0.0%** | `None` |
| `SCEN_SMALL_02` | SMALL | 6 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 4.0/6 | 7.2 | 0.24 / 0.26 / 0.27ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **5.0/6** | **7.02** | **116.8 / 124.11 / 125.94ms** | True | **100.0%** | **100.0%** | **0.0%** | `None` |
| `SCEN_SMALL_03` | SMALL | 8 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 8.0/8 | 8.0 | 0.59 / 0.77 / 0.81ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **8.0/8** | **8.0** | **196.28 / 200.31 / 201.32ms** | True | **100.0%** | **100.0%** | **0.0%** | `None` |
| `SCEN_MED_01` | MEDIUM | 10 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 7.0/10 | 6.92 | 0.43 / 0.51 / 0.53ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **7.0/10** | **6.92** | **1624.77 / 1654.68 / 1662.16ms** | True | **100.0%** | **0.0%** | **0.0%** | `None` |
| `SCEN_MED_02` | MEDIUM | 12 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 12.0/12 | 12.0 | 3.43 / 3.49 / 3.51ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **12.0/12** | **12.0** | **2061.68 / 2067.73 / 2069.24ms** | True | **100.0%** | **0.0%** | **0.0%** | `None` |
| `SCEN_MED_03` | MEDIUM | 15 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 15.0/15 | 15.0 | 1.56 / 1.6 / 1.61ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **15.0/15** | **15.0** | **2085.11 / 2086.33 / 2086.64ms** | True | **100.0%** | **0.0%** | **0.0%** | `None` |
| `SCEN_LARGE_01` | LARGER | 20 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 20.0/20 | 18.6 | 5.24 / 5.66 / 5.76ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **20.0/20** | **18.6** | **3873.86 / 3898.62 / 3904.81ms** | True | **100.0%** | **0.0%** | **0.0%** | `None` |
| `SCEN_LARGE_02` | LARGER | 24 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 24.0/24 | 25.44 | 8.24 / 8.5 / 8.56ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **24.0/24** | **25.44** | **5429.08 / 5465.15 / 5474.17ms** | True | **100.0%** | **0.0%** | **0.0%** | `None` |
| `SCEN_INFEASIBLE_01` | SMALL | 1 | Strict Complete | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | UNKNOWN | 0.0/1 | 0.0 | 0.03 / 0.04 / 0.04ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **INFEASIBLE** | **0.0/1** | **0.0** | **0.71 / 0.81 / 0.84ms** | True | **0.0%** | **0.0%** | **100.0%** | `None` |
| `SCEN_INFEASIBLE_02` | SMALL | 2 | Strict Complete | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | UNKNOWN | 1.0/2 | 1.0 | 0.13 / 0.14 / 0.14ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **INFEASIBLE** | **0.0/2** | **0.0** | **1.92 / 2.38 / 2.49ms** | True | **0.0%** | **0.0%** | **100.0%** | `None` |
| `SCEN_SYNTH_01` | MEDIUM | 14 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 12.0/14 | 8.4 | 2.07 / 2.15 / 2.17ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **12.0/14** | **8.4** | **2265.65 / 2278.8 / 2282.09ms** | True | **100.0%** | **0.0%** | **0.0%** | `None` |
| `SCEN_BOUND_01` | MEDIUM | 16 | Best-Effort | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 16.0/16 | 12.96 | 1.78 / 1.88 / 1.91ms | True | 0.0% | 0.0% | 0.0% | None |
| | | | | `CP-SAT` | `PYTHON_HEURISTIC_EP_3D_V1` | **FEASIBLE** | **16.0/16** | **12.96** | **643.27 / 645.65 / 646.24ms** | True | **0.0%** | **0.0%** | **0.0%** | `CPSAT_UNKNOWN` |

## 4. Reconciled Cohort Summary by Instance Size

| Cohort Group | Scenarios (Opt + Infeas) | Total Trials | CP-SAT Primary Opt Proof % | CP-SAT Full Lex Proof % | CP-SAT Infeas Proof % | Complete Feasibility Rate (H vs CP) | Candidate Validation Rate | Heuristic Median Latency | CP-SAT Median Latency | Fallback Rate |
|---|---|---|---|---|---|---|---|---|---|---|
| **SMALL** | 5 (3 + 2) | 15 per solver | 9/9 (100.0%) | 9/9 (100.0%) | 6/6 (100.0%) | 60.0% vs 60.0% | 9/9 (100%) | 0.14ms | 21.08ms | 0/15 (0.0%) |
| **MEDIUM** | 5 (5 + 0) | 15 per solver | 12/15 (80.0%) | 0/15 (0.0%) | N/A | 100.0% vs 100.0% | 15/15 (100%) | 1.78ms | 2061.68ms | 3/15 (20.0%) |
| **LARGER** | 2 (2 + 0) | 6 per solver | 6/6 (100.0%) | 0/6 (0.0%) | N/A | 100.0% vs 100.0% | 6/6 (100%) | 6.55ms | 4625.02ms | 0/6 (0.0%) |

## 5. Global Reconciled Metrics Across All 12 Scenarios
- **Total Experimental Invocations**: `72` runs (3 measured trials per solver across 12 scenarios).
- **Primary Optimization Proof Rate (Eligible Optimization Runs)**: `27/30` (**90.0%**). Denominator strictly excludes infeasibility scenarios.
- **Full Lexicographic Proof Rate (Eligible Optimization Runs)**: `9/30` (**30.0%**). Proved for all 3 Small optimization instances; bounded to incumbent for Medium/Larger scale.
- **Infeasibility Proof Rate (Eligible Infeasibility Runs)**: `6/6` (**100.0%**). CP-SAT mathematically proved 100% of declared infeasible manifests; Heuristic correctly emitted `UNKNOWN` (0% false feasibility).
- **Independent Candidate Validation Pass Rate**: **100%** across both algorithms (`33/33` Heuristic, `30/30` CP-SAT).
- **Graceful Heuristic Fallback Rate**: `3/36` (**8.3%**). Specifically restricted to `SCEN_BOUND_01` (1s micro-budget) where heuristic incumbent was preserved without false optimality.