package org.soa8.freight.optimization.model;

import java.util.Set;

/**
 * Authoritative Job Execution States and Legal State Transitions.
 */
public enum JobStatus {
    SUBMITTED,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == TIMEOUT || this == CANCELLED;
    }

    public boolean canTransitionTo(JobStatus next) {
        if (this == next) return true; // idempotent
        return switch (this) {
            case SUBMITTED -> next == RUNNING || next == COMPLETED || next == FAILED || next == TIMEOUT || next == CANCELLED;
            case RUNNING -> next == COMPLETED || next == FAILED || next == TIMEOUT || next == CANCELLED;
            case COMPLETED, FAILED, TIMEOUT, CANCELLED -> false; // Terminal states cannot transition
        };
    }
}
