# Phase F.4 — Benchmark Integrity & Repeated-Trial Evaluation Report

## 1. Experimental Environment & Provenance Metadata
- **OS / Platform**: `Windows 11 (AMD64)`
- **Python Version**: `3.12.10`
- **Total Scenarios**: `12`
- **Measured Trials per Scenario**: `3` (each trial executed with deterministic seeds)
- **Validation Gate**: Strict independent 3D physics and safety check applied before counting feasibility.

## 2. Solver Status & Optimality Semantics
- **Primary Objective Proven (`primaryObjectiveProven`)**: Mathematical proof of maximum assignable cargo count (or proven infeasibility for complete placement).
- **Full Lexicographic Optimality (`fullLexicographicOptimalityProven`)**: Strictly reserved for runs where all three stages (Count, Volume, and Compactness) proved optimality.
- **API Backward Compatibility**: API solver status `OPTIMAL` corresponds to proven primary count optimality. `fullLexicographicOptimalityProven` provides unambiguous full-depth proof qualification.

## 3. Per-Scenario Statistical Summary (Across Repeated Trials)

| Scenario ID | Size | Units | Algorithm | Effective Algorithm | Solver Status | Placed (avg) | Vol ($m^3$) | Latency (med / p90 / max) | Valid Rate | Primary Proof % | Full Lex Proof % | Fallback |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `SCEN_SMALL_01` | SMALL | 4 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 4.0/4 | 4.0 | 0.12 / 0.18 / 0.19ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **4.0/4** | **4.0** | **21.58 / 24.24 / 24.91ms** | 100.0% | **100.0%** | **100.0%** | `None` |
| `SCEN_SMALL_02` | SMALL | 6 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 4.0/6 | 7.2 | 0.16 / 0.21 / 0.22ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **5.0/6** | **7.02** | **100.59 / 110.19 / 112.59ms** | 100.0% | **100.0%** | **100.0%** | `None` |
| `SCEN_SMALL_03` | SMALL | 8 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 8.0/8 | 8.0 | 0.48 / 0.59 / 0.62ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **8.0/8** | **8.0** | **209.16 / 214.78 / 216.18ms** | 100.0% | **100.0%** | **100.0%** | `None` |
| `SCEN_MED_01` | MEDIUM | 10 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 7.0/10 | 6.92 | 0.57 / 0.67 / 0.7ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **7.0/10** | **6.92** | **1611.85 / 1618.5 / 1620.16ms** | 100.0% | **100.0%** | **0.0%** | `None` |
| `SCEN_MED_02` | MEDIUM | 12 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 12.0/12 | 12.0 | 2.39 / 2.75 / 2.84ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **12.0/12** | **12.0** | **2090.9 / 2121.15 / 2128.71ms** | 100.0% | **100.0%** | **0.0%** | `None` |
| `SCEN_MED_03` | MEDIUM | 15 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 15.0/15 | 15.0 | 2.13 / 2.16 / 2.17ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **15.0/15** | **15.0** | **2116.05 / 2154.95 / 2164.67ms** | 100.0% | **100.0%** | **0.0%** | `None` |
| `SCEN_LARGE_01` | LARGER | 20 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 20.0/20 | 18.6 | 4.08 / 4.28 / 4.33ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **20.0/20** | **18.6** | **3866.64 / 3993.13 / 4024.75ms** | 100.0% | **100.0%** | **0.0%** | `None` |
| `SCEN_LARGE_02` | LARGER | 24 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 24.0/24 | 25.44 | 8.49 / 9.33 / 9.54ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **24.0/24** | **25.44** | **5517.52 / 5596.62 / 5616.39ms** | 100.0% | **100.0%** | **0.0%** | `None` |
| `SCEN_INFEASIBLE_01` | SMALL | 1 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | UNKNOWN | 0.0/1 | 0.0 | 0.03 / 0.05 / 0.05ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **INFEASIBLE** | **0.0/1** | **0.0** | **0.97 / 1.07 / 1.09ms** | 100.0% | **100.0%** | **0.0%** | `None` |
| `SCEN_INFEASIBLE_02` | SMALL | 2 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | UNKNOWN | 1.0/2 | 1.0 | 0.15 / 0.19 / 0.2ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **INFEASIBLE** | **0.0/2** | **0.0** | **3.23 / 3.81 / 3.95ms** | 100.0% | **100.0%** | **0.0%** | `None` |
| `SCEN_SYNTH_01` | MEDIUM | 14 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 12.0/14 | 8.4 | 2.93 / 4.35 / 4.71ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **12.0/14** | **8.4** | **2244.64 / 2259.44 / 2263.14ms** | 100.0% | **100.0%** | **0.0%** | `None` |
| `SCEN_BOUND_01` | MEDIUM | 16 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 16.0/16 | 12.96 | 1.91 / 1.94 / 1.95ms | 100.0% | 0.0% | 0.0% | None |
| | | | `CP-SAT` | `PYTHON_HEURISTIC_EP_3D_V1` | **FEASIBLE** | **16.0/16** | **12.96** | **635.4 / 681.67 / 693.24ms** | 100.0% | **0.0%** | **0.0%** | `CPSAT_UNKNOWN` |

## 4. Cohort Aggregates by Instance Size

| Cohort Group | Scenario Count | Total Measured Trials | Heuristic Avg Placed % | CP-SAT Avg Placed % | Heuristic Median Latency | CP-SAT Median Latency | CP-SAT Primary Proof % | CP-SAT Full Lex Proof % | Validation Pass Rate | Fallback Rate |
|---|---|---|---|---|---|---|---|---|---|---|
| **SMALL** | 5 | 15 per solver | 63.3% | 56.7% | 0.15ms | 21.58ms | 15/15 (100.0%) | 9/15 (60.0%) | 30/30 (100%) | 0/15 (0.0%) |
| **MEDIUM** | 5 | 15 per solver | 91.1% | 91.1% | 2.13ms | 2050.18ms | 12/15 (80.0%) | 0/15 (0.0%) | 30/30 (100%) | 3/15 (20.0%) |
| **LARGER** | 2 | 6 per solver | 100.0% | 100.0% | 6.02ms | 4742.53ms | 6/6 (100.0%) | 0/6 (0.0%) | 12/12 (100%) | 0/6 (0.0%) |

## 5. Audit Conclusions & Findings on Infeasibility
- **SCEN_INFEASIBLE_01**: Single unit exceeds compartment dimension with `requireAllPlaced=True`. Both algorithms correctly identify failure to place all units: CP-SAT proves `INFEASIBLE` with $0/1$ placed; Heuristic reports `UNKNOWN` with $0/1$ placed without false optimality.
- **SCEN_INFEASIBLE_02 Audit**: Manifest contains Class 1 Explosives and Class 2.1 Flammable Gas under CMV statutory rules forbidding co-loading (`FORBIDDEN`), with `requireAllPlaced=True`. CP-SAT proves mathematical `INFEASIBLE` (0/2 placed). Heuristic places 1/2 and now strictly reports `UNKNOWN` under `requireAllPlaced=True` rather than falsely declaring complete set feasibility.