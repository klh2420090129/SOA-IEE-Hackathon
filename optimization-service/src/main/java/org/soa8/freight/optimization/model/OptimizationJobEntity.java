package org.soa8.freight.optimization.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "optimization_jobs")
public class OptimizationJobEntity {
    @Id
    private String jobId;

    private String declarationId;
    private String compartmentId;
    private String status;       // SUBMITTED, RUNNING, COMPLETED, FAILED
    private String solverStatus; // FEASIBLE, INFEASIBLE, UNKNOWN, TIMEOUT
    private String provenance;   // CONTROLLED_STUB or REAL_OPTIMIZER

    @Lob
    private String resultJson;

    private Instant createdAt;
    private Instant completedAt;

    public OptimizationJobEntity() {}

    public OptimizationJobEntity(String jobId, String declarationId, String compartmentId) {
        this.jobId = jobId;
        this.declarationId = declarationId;
        this.compartmentId = compartmentId;
        this.status = "SUBMITTED";
        this.solverStatus = "UNKNOWN";
        this.provenance = "CONTROLLED_STUB";
        this.createdAt = Instant.now();
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getDeclarationId() { return declarationId; }
    public void setDeclarationId(String declarationId) { this.declarationId = declarationId; }

    public String getCompartmentId() { return compartmentId; }
    public void setCompartmentId(String compartmentId) { this.compartmentId = compartmentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSolverStatus() { return solverStatus; }
    public void setSolverStatus(String solverStatus) { this.solverStatus = solverStatus; }

    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
