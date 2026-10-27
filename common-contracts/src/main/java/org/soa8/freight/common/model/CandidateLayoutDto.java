package org.soa8.freight.common.model;

import java.util.ArrayList;
import java.util.List;

public class CandidateLayoutDto {
    private String layoutId;
    private int layoutVersion;
    private String compartmentId;
    private String contentDigest; // SHA-256
    private String solverStatus;  // OPTIMAL, FEASIBLE, INFEASIBLE, UNKNOWN, TIMEOUT
    private String provenance;    // CONTROLLED_STUB or REAL_OPTIMIZER / OR_TOOLS_CPSAT_V1
    private String ruleSnapshotId;
    private Long executionDurationMs;
    private List<ItemPlacementDto> placements = new ArrayList<>();
    private List<UnassignedItemDto> unassignedItems = new ArrayList<>();
    private String diagnostics;

    public CandidateLayoutDto() {
    }

    public CandidateLayoutDto(String layoutId, int layoutVersion, String compartmentId, String contentDigest,
                              String solverStatus, String provenance, List<ItemPlacementDto> placements) {
        this.layoutId = layoutId;
        this.layoutVersion = layoutVersion;
        this.compartmentId = compartmentId;
        this.contentDigest = contentDigest;
        this.solverStatus = solverStatus;
        this.provenance = provenance;
        this.placements = placements != null ? placements : new ArrayList<>();
    }

    public CandidateLayoutDto(String layoutId, int layoutVersion, String compartmentId, String contentDigest,
                              String solverStatus, String provenance, String ruleSnapshotId,
                              Long executionDurationMs, List<ItemPlacementDto> placements,
                              List<UnassignedItemDto> unassignedItems, String diagnostics) {
        this.layoutId = layoutId;
        this.layoutVersion = layoutVersion;
        this.compartmentId = compartmentId;
        this.contentDigest = contentDigest;
        this.solverStatus = solverStatus;
        this.provenance = provenance;
        this.ruleSnapshotId = ruleSnapshotId;
        this.executionDurationMs = executionDurationMs;
        this.placements = placements != null ? placements : new ArrayList<>();
        this.unassignedItems = unassignedItems != null ? unassignedItems : new ArrayList<>();
        this.diagnostics = diagnostics;
    }

    public String getLayoutId() { return layoutId; }
    public void setLayoutId(String layoutId) { this.layoutId = layoutId; }

    public int getLayoutVersion() { return layoutVersion; }
    public void setLayoutVersion(int layoutVersion) { this.layoutVersion = layoutVersion; }

    public String getCompartmentId() { return compartmentId; }
    public void setCompartmentId(String compartmentId) { this.compartmentId = compartmentId; }

    public String getContentDigest() { return contentDigest; }
    public void setContentDigest(String contentDigest) { this.contentDigest = contentDigest; }

    public String getSolverStatus() { return solverStatus; }
    public void setSolverStatus(String solverStatus) { this.solverStatus = solverStatus; }

    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }

    public String getRuleSnapshotId() { return ruleSnapshotId; }
    public void setRuleSnapshotId(String ruleSnapshotId) { this.ruleSnapshotId = ruleSnapshotId; }

    public Long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public List<ItemPlacementDto> getPlacements() { return placements; }
    public void setPlacements(List<ItemPlacementDto> placements) { this.placements = placements; }

    public List<UnassignedItemDto> getUnassignedItems() { return unassignedItems; }
    public void setUnassignedItems(List<UnassignedItemDto> unassignedItems) { this.unassignedItems = unassignedItems; }

    public String getDiagnostics() { return diagnostics; }
    public void setDiagnostics(String diagnostics) { this.diagnostics = diagnostics; }
}
