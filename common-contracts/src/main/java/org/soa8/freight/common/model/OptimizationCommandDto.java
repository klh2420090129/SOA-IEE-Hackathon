package org.soa8.freight.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Versioned Kafka Command carrying an optimization dispatch to the worker.
 * Topic: soa.s06.t08.optimization.commands
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OptimizationCommandDto {
    public static final String CURRENT_SCHEMA_VERSION = "1.0.0";

    private String commandId;
    private String jobId;
    private String attemptId;
    private String schemaVersion;
    private Instant occurredAt;
    private String correlationId;

    private String ruleSnapshotId;
    private String ruleSnapshotDigest;
    private RuleSnapshotDto ruleSnapshot;

    private String declarationId;
    private List<CargoItemDto> items;
    private CompartmentDto compartment;

    private String algorithm; // HEURISTIC_EP_3D_V1 or CONTROLLED_STUB
    private int timeLimitSeconds;
    private Map<String, Object> parameters;

    public OptimizationCommandDto() {
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.occurredAt = Instant.now();
    }

    public OptimizationCommandDto(String commandId, String jobId, String attemptId, String correlationId,
                                  String ruleSnapshotId, String ruleSnapshotDigest, RuleSnapshotDto ruleSnapshot,
                                  String declarationId, List<CargoItemDto> items, CompartmentDto compartment,
                                  String algorithm, int timeLimitSeconds, Map<String, Object> parameters) {
        this.commandId = commandId;
        this.jobId = jobId;
        this.attemptId = attemptId;
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.occurredAt = Instant.now();
        this.correlationId = correlationId;
        this.ruleSnapshotId = ruleSnapshotId;
        this.ruleSnapshotDigest = ruleSnapshotDigest;
        this.ruleSnapshot = ruleSnapshot;
        this.declarationId = declarationId;
        this.items = items;
        this.compartment = compartment;
        this.algorithm = algorithm;
        this.timeLimitSeconds = timeLimitSeconds;
        this.parameters = parameters;
    }

    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getRuleSnapshotId() { return ruleSnapshotId; }
    public void setRuleSnapshotId(String ruleSnapshotId) { this.ruleSnapshotId = ruleSnapshotId; }

    public String getRuleSnapshotDigest() { return ruleSnapshotDigest; }
    public void setRuleSnapshotDigest(String ruleSnapshotDigest) { this.ruleSnapshotDigest = ruleSnapshotDigest; }

    public RuleSnapshotDto getRuleSnapshot() { return ruleSnapshot; }
    public void setRuleSnapshot(RuleSnapshotDto ruleSnapshot) { this.ruleSnapshot = ruleSnapshot; }

    public String getDeclarationId() { return declarationId; }
    public void setDeclarationId(String declarationId) { this.declarationId = declarationId; }

    public List<CargoItemDto> getItems() { return items; }
    public void setItems(List<CargoItemDto> items) { this.items = items; }

    public CompartmentDto getCompartment() { return compartment; }
    public void setCompartment(CompartmentDto compartment) { this.compartment = compartment; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public int getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(int timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
