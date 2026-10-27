package org.soa8.freight.common.model;

/**
 * Authoritative Solver Outcomes.
 * Separate from Job Execution State (SUBMITTED, RUNNING, COMPLETED, FAILED, TIMEOUT, CANCELLED).
 */
public enum SolverStatus {
    OPTIMAL,
    FEASIBLE,
    INFEASIBLE,
    UNKNOWN,
    TIMEOUT
}
