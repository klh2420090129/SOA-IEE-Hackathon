package org.soa8.freight.optimization.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "optimization_jobs")
public class OptimizationJobEntity {
    @Id
    private String jobId;

    private String declarationId;
    private String compartmentId;
    private String ruleSnapshotId;

    @Enumerated(EnumType.STRING)
    private JobStatus status;       // SUBMITTED, RUNNING, COMPLETED, FAILED, TIMEOUT, CANCELLED

    private String solverStatus; // OPTIMAL, FEASIBLE, INFEASIBLE, UNKNOWN, TIMEOUT
    private String provenance;   // CONTROLLED_STUB, OR_TOOLS_CPSAT_V1, etc.

    private Integer timeLimitSeconds; // Configured solver budget (default 20, max 30)
    private Long executionDurationMs;

    @Lob
    private String resultJson;

    @Lob
    private String errorMessage;

    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    public OptimizationJobEntity() {}

    public OptimizationJobEntity(String jobId, String declarationId, String compartmentId, String ruleSnapshotId) {
        this.jobId = jobId;
        this.declarationId = declarationId;
        this.compartmentId = compartmentId;
        this.ruleSnapshotId = ruleSnapshotId;
        this.status = JobStatus.SUBMITTED;
        this.solverStatus = "UNKNOWN";
        this.provenance = "CONTROLLED_STUB";
        this.timeLimitSeconds = 20;
        this.createdAt = Instant.now();
    }

    public void transitionTo(JobStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException("Illegal job status transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getDeclarationId() { return declarationId; }
    public void setDeclarationId(String declarationId) { this.declarationId = declarationId; }

    public String getCompartmentId() { return compartmentId; }
    public void setCompartmentId(String compartmentId) { this.compartmentId = compartmentId; }

    public String getRuleSnapshotId() { return ruleSnapshotId; }
    public void setRuleSnapshotId(String ruleSnapshotId) { this.ruleSnapshotId = ruleSnapshotId; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public String getSolverStatus() { return solverStatus; }
    public void setSolverStatus(String solverStatus) { this.solverStatus = solverStatus; }

    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }

    public Integer getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(Integer timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }

    public Long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
