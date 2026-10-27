package org.soa8.freight.compartment.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "assessments")
public class AssessmentEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String declarationId;

    private int declarationVersion;
    private String ruleSnapshotId;

    // Workflow state: DRAFT, OPTIMIZING, CANDIDATE_READY, PENDING_APPROVAL, APPROVED, REJECTED, VALIDATION_FAILED, BLOCKED_UNVERIFIED_RULES
    @Column(nullable = false)
    private String workflowStatus;

    private String compartmentId;
    private int compartmentLengthMm;
    private int compartmentWidthMm;
    private int compartmentHeightMm;
    private double compartmentMaxPayloadKg;

    private String candidateLayoutId;
    private int candidateLayoutVersion;
    private String candidateContentDigest;

    private String validationId;
    private boolean validationPassed;

    private String approvalDecision; // APPROVED, REJECTED
    private String decidedBy;
    private String decisionReasonCode;
    private String decisionNotes;
    private Instant decisionTimestamp;

    private Instant createdAt;
    private Instant updatedAt;

    public AssessmentEntity() {}

    public AssessmentEntity(String id, String declarationId, int declarationVersion, String ruleSnapshotId,
                            String compartmentId, int lengthMm, int widthMm, int heightMm, double maxPayloadKg) {
        this.id = id;
        this.declarationId = declarationId;
        this.declarationVersion = declarationVersion;
        this.ruleSnapshotId = ruleSnapshotId;
        this.compartmentId = compartmentId;
        this.compartmentLengthMm = lengthMm;
        this.compartmentWidthMm = widthMm;
        this.compartmentHeightMm = heightMm;
        this.compartmentMaxPayloadKg = maxPayloadKg;
        this.workflowStatus = "DRAFT";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDeclarationId() { return declarationId; }
    public void setDeclarationId(String declarationId) { this.declarationId = declarationId; }

    public int getDeclarationVersion() { return declarationVersion; }
    public void setDeclarationVersion(int declarationVersion) { this.declarationVersion = declarationVersion; }

    public String getRuleSnapshotId() { return ruleSnapshotId; }
    public void setRuleSnapshotId(String ruleSnapshotId) { this.ruleSnapshotId = ruleSnapshotId; }

    public String getWorkflowStatus() { return workflowStatus; }
    public void setWorkflowStatus(String workflowStatus) { this.workflowStatus = workflowStatus; }

    public String getCompartmentId() { return compartmentId; }
    public void setCompartmentId(String compartmentId) { this.compartmentId = compartmentId; }

    public int getCompartmentLengthMm() { return compartmentLengthMm; }
    public void setCompartmentLengthMm(int compartmentLengthMm) { this.compartmentLengthMm = compartmentLengthMm; }

    public int getCompartmentWidthMm() { return compartmentWidthMm; }
    public void setCompartmentWidthMm(int compartmentWidthMm) { this.compartmentWidthMm = compartmentWidthMm; }

    public int getCompartmentHeightMm() { return compartmentHeightMm; }
    public void setCompartmentHeightMm(int compartmentHeightMm) { this.compartmentHeightMm = compartmentHeightMm; }

    public double getCompartmentMaxPayloadKg() { return compartmentMaxPayloadKg; }
    public void setCompartmentMaxPayloadKg(double compartmentMaxPayloadKg) { this.compartmentMaxPayloadKg = compartmentMaxPayloadKg; }

    public String getCandidateLayoutId() { return candidateLayoutId; }
    public void setCandidateLayoutId(String candidateLayoutId) { this.candidateLayoutId = candidateLayoutId; }

    public int getCandidateLayoutVersion() { return candidateLayoutVersion; }
    public void setCandidateLayoutVersion(int candidateLayoutVersion) { this.candidateLayoutVersion = candidateLayoutVersion; }

    public String getCandidateContentDigest() { return candidateContentDigest; }
    public void setCandidateContentDigest(String candidateContentDigest) { this.candidateContentDigest = candidateContentDigest; }

    public String getValidationId() { return validationId; }
    public void setValidationId(String validationId) { this.validationId = validationId; }

    public boolean isValidationPassed() { return validationPassed; }
    public void setValidationPassed(boolean validationPassed) { this.validationPassed = validationPassed; }

    public String getApprovalDecision() { return approvalDecision; }
    public void setApprovalDecision(String approvalDecision) { this.approvalDecision = approvalDecision; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public String getDecisionReasonCode() { return decisionReasonCode; }
    public void setDecisionReasonCode(String decisionReasonCode) { this.decisionReasonCode = decisionReasonCode; }

    public String getDecisionNotes() { return decisionNotes; }
    public void setDecisionNotes(String decisionNotes) { this.decisionNotes = decisionNotes; }

    public Instant getDecisionTimestamp() { return decisionTimestamp; }
    public void setDecisionTimestamp(Instant decisionTimestamp) { this.decisionTimestamp = decisionTimestamp; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
