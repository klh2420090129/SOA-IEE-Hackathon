package org.soa8.freight.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Versioned Kafka Result emitted by the worker upon job completion or failure.
 * Topic: soa.s06.t08.optimization.results
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OptimizationResultDto {
    public static final String CURRENT_SCHEMA_VERSION = "1.0.0";

    private String resultId;
    private String jobId;
    private String attemptId;
    private String schemaVersion;
    private Instant completedAt;
    private String correlationId;

    private String solverStatus; // FEASIBLE, INFEASIBLE, UNKNOWN, TIMEOUT
    private String provenance;   // e.g. PYTHON_HEURISTIC_EP_3D_V1
    private Long executionDurationMs;

    private String ruleSnapshotId;
    private String ruleSnapshotDigest;
    private String contentDigest;

    private List<ItemPlacementDto> placements;
    private List<UnassignedItemDto> unassignedItems;
    private Map<String, Object> diagnostics;
    private String errorMessage;

    public OptimizationResultDto() {
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.completedAt = Instant.now();
    }

    public OptimizationResultDto(String resultId, String jobId, String attemptId, String correlationId,
                                 String solverStatus, String provenance, Long executionDurationMs,
                                 String ruleSnapshotId, String ruleSnapshotDigest, String contentDigest,
                                 List<ItemPlacementDto> placements, List<UnassignedItemDto> unassignedItems,
                                 Map<String, Object> diagnostics, String errorMessage) {
        this.resultId = resultId;
        this.jobId = jobId;
        this.attemptId = attemptId;
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.completedAt = Instant.now();
        this.correlationId = correlationId;
        this.solverStatus = solverStatus;
        this.provenance = provenance;
        this.executionDurationMs = executionDurationMs;
        this.ruleSnapshotId = ruleSnapshotId;
        this.ruleSnapshotDigest = ruleSnapshotDigest;
        this.contentDigest = contentDigest;
        this.placements = placements;
        this.unassignedItems = unassignedItems;
        this.diagnostics = diagnostics;
        this.errorMessage = errorMessage;
    }

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getSolverStatus() { return solverStatus; }
    public void setSolverStatus(String solverStatus) { this.solverStatus = solverStatus; }

    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }

    public Long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public String getRuleSnapshotId() { return ruleSnapshotId; }
    public void setRuleSnapshotId(String ruleSnapshotId) { this.ruleSnapshotId = ruleSnapshotId; }

    public String getRuleSnapshotDigest() { return ruleSnapshotDigest; }
    public void setRuleSnapshotDigest(String ruleSnapshotDigest) { this.ruleSnapshotDigest = ruleSnapshotDigest; }

    public String getContentDigest() { return contentDigest; }
    public void setContentDigest(String contentDigest) { this.contentDigest = contentDigest; }

    public List<ItemPlacementDto> getPlacements() { return placements; }
    public void setPlacements(List<ItemPlacementDto> placements) { this.placements = placements; }

    public List<UnassignedItemDto> getUnassignedItems() { return unassignedItems; }
    public void setUnassignedItems(List<UnassignedItemDto> unassignedItems) { this.unassignedItems = unassignedItems; }

    public Map<String, Object> getDiagnostics() { return diagnostics; }
    public void setDiagnostics(Map<String, Object> diagnostics) { this.diagnostics = diagnostics; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
