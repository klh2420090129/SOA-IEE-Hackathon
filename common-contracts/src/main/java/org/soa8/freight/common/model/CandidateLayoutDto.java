package org.soa8.freight.common.model;

import java.util.List;

public class CandidateLayoutDto {
    private String layoutId;
    private int layoutVersion;
    private String compartmentId;
    private String contentDigest; // SHA-256
    private String solverStatus;  // FEASIBLE, OPTIMAL, etc.
    private String provenance;    // CONTROLLED_STUB or REAL_OPTIMIZER
    private List<ItemPlacementDto> placements;

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
        this.placements = placements;
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

    public List<ItemPlacementDto> getPlacements() { return placements; }
    public void setPlacements(List<ItemPlacementDto> placements) { this.placements = placements; }
}
