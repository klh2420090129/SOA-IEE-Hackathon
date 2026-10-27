package org.soa8.freight.compartment.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "candidate_layouts")
public class CandidateLayoutEntity {
    @Id
    private String layoutId;

    private int layoutVersion;
    private String assessmentId;
    private String compartmentId;
    private String contentDigest;
    private String solverStatus;
    private String provenance; // CONTROLLED_STUB or REAL_OPTIMIZER

    @Lob
    private String placementsJson;

    private Instant createdAt;

    public CandidateLayoutEntity() {}

    public CandidateLayoutEntity(String layoutId, int layoutVersion, String assessmentId,
                                 String compartmentId, String contentDigest, String solverStatus,
                                 String provenance, String placementsJson) {
        this.layoutId = layoutId;
        this.layoutVersion = layoutVersion;
        this.assessmentId = assessmentId;
        this.compartmentId = compartmentId;
        this.contentDigest = contentDigest;
        this.solverStatus = solverStatus;
        this.provenance = provenance;
        this.placementsJson = placementsJson;
        this.createdAt = Instant.now();
    }

    public String getLayoutId() { return layoutId; }
    public void setLayoutId(String layoutId) { this.layoutId = layoutId; }

    public int getLayoutVersion() { return layoutVersion; }
    public void setLayoutVersion(int layoutVersion) { this.layoutVersion = layoutVersion; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public String getCompartmentId() { return compartmentId; }
    public void setCompartmentId(String compartmentId) { this.compartmentId = compartmentId; }

    public String getContentDigest() { return contentDigest; }
    public void setContentDigest(String contentDigest) { this.contentDigest = contentDigest; }

    public String getSolverStatus() { return solverStatus; }
    public void setSolverStatus(String solverStatus) { this.solverStatus = solverStatus; }

    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }

    public String getPlacementsJson() { return placementsJson; }
    public void setPlacementsJson(String placementsJson) { this.placementsJson = placementsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
