# Phase F.3 — Optimization Benchmark Report

## Overview
Deterministic benchmark comparison between `HEURISTIC_EP_3D_V1` and `PYTHON_ORTOOLS_CPSAT_3D_V1` across 12 canonical scenarios.

## Per-Scenario Evaluation Results

| Scenario ID | Size | Units | Algorithm | Effective Executed | Status | Placed | Vol (m³) | Util % | Solver Time | Latency | Valid? | Stage Proofs |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `SCEN_SMALL_01` | SMALL | 4 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 4/4 | 4.0 | 100.0% | 0ms | 0.17ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **4/4** | **4.0** | **100.0%** | 6.76ms | 40.28ms | True | `Count+Vol+Compact` |
| `SCEN_SMALL_02` | SMALL | 6 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 4/6 | 7.2 | 66.67% | 0ms | 0.2ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **5/6** | **7.02** | **65.0%** | 63.4ms | 108.68ms | True | `Count+Vol+Compact` |
| `SCEN_SMALL_03` | SMALL | 8 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 8/8 | 8.0 | 90.91% | 0ms | 0.42ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **8/8** | **8.0** | **90.91%** | 38.11ms | 161.93ms | True | `Count+Vol+Compact` |
| `SCEN_MED_01` | MEDIUM | 10 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 7/10 | 6.92 | 20.02% | 0ms | 0.47ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **7/10** | **6.92** | **20.02%** | 1201.71ms | 1589.29ms | True | `Count+Vol` |
| `SCEN_MED_02` | MEDIUM | 12 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 12/12 | 12.0 | 26.04% | 4ms | 2.27ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **12/12** | **12.0** | **26.04%** | 1602.93ms | 1996.18ms | True | `Count+Vol` |
| `SCEN_MED_03` | MEDIUM | 15 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 15/15 | 15.0 | 93.75% | 4ms | 1.45ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **15/15** | **15.0** | **93.75%** | 1612.68ms | 2036.19ms | True | `Count+Vol` |
| `SCEN_LARGE_01` | LARGER | 20 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 20/20 | 18.6 | 26.91% | 5ms | 5.43ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **20/20** | **18.6** | **26.91%** | 1602.57ms | 3723.34ms | True | `Count+Vol` |
| `SCEN_LARGE_02` | LARGER | 24 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 24/24 | 25.44 | 36.81% | 8ms | 8.19ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **24/24** | **25.44** | **36.81%** | 1601.03ms | 5183.92ms | True | `Count+Vol` |
| `SCEN_INFEASIBLE_01` | SMALL | 1 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | UNKNOWN | 0/1 | 0.0 | 0.0% | 0ms | 0.03ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **INFEASIBLE** | **0/1** | **0.0** | **0.0%** | 0ms | 0.72ms | True | `Incumbent` |
| `SCEN_INFEASIBLE_02` | SMALL | 2 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 1/2 | 1.0 | 2.89% | 0ms | 0.1ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **INFEASIBLE** | **0/2** | **0.0** | **0.0%** | 0ms | 1.39ms | True | `Incumbent` |
| `SCEN_SYNTH_01` | MEDIUM | 14 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 12/14 | 8.4 | 25.0% | 4ms | 2.2ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_ORTOOLS_CPSAT_3D_V1` | **OPTIMAL** | **12/14** | **8.4** | **25.0%** | 1602.48ms | 2205.89ms | True | `Count+Vol` |
| `SCEN_BOUND_01` | MEDIUM | 16 | `HEURISTIC` | `PYTHON_HEURISTIC_EP_3D_V1` | FEASIBLE | 16/16 | 12.96 | 45.0% | 4ms | 1.75ms | True | N/A |
| | | | `CP-SAT` | `PYTHON_HEURISTIC_EP_3D_V1` | **FEASIBLE** | **16/16** | **12.96** | **45.0%** | 0ms | 651.67ms | True | `Incumbent` |

## Aggregate Summary by Instance Size

| Instance Size | Scenarios | Heuristic Avg Placed % | CP-SAT Avg Placed % | Heuristic Median Latency | CP-SAT Median Latency | CP-SAT Proof Rate | Independent Validation Rate |
|---|---|---|---|---|---|---|---|
| **SMALL** | 5 | 63.3% | 56.7% | 0.17ms | 40.28ms | 3/5 (60.0%) | 10/10 (100%) |
| **MEDIUM** | 5 | 91.1% | 91.1% | 1.75ms | 1996.18ms | 4/5 (80.0%) | 10/10 (100%) |
| **LARGER** | 2 | 100.0% | 100.0% | 8.19ms | 5183.92ms | 2/2 (100.0%) | 4/4 (100%) |

## Notes & Provenance Rules
- Statutory rule snapshots (`snap-2026-q4-v1`) reflect verified India CMV regulations.
- Synthetic snapshots are explicitly tagged and segregated from statutory regulations.
- CP-SAT provides mathematical proofs of optimality or infeasibility for bounded instances.