package org.soa8.freight.compartment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "assessment_decision_audit")
public class AssessmentDecisionAuditEntity {

    @Id
    private String auditId;

    @Column(nullable = false)
    private String assessmentId;

    @Column(nullable = false)
    private String declarationId;

    private int declarationVersion;
    private int layoutVersion;
    private String layoutDigest;
    private String ruleSnapshotId;
    private String validationId;

    @Column(nullable = false)
    private String decision; // APPROVED or REJECTED

    private String reasonCode;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private String decidedBy;

    @Column(nullable = false)
    private Instant decidedAt;

    private String correlationId;

    public AssessmentDecisionAuditEntity() {}

    public AssessmentDecisionAuditEntity(String auditId, String assessmentId, String declarationId,
                                         int declarationVersion, int layoutVersion, String layoutDigest,
                                         String ruleSnapshotId, String validationId, String decision,
                                         String reasonCode, String notes, String decidedBy,
                                         Instant decidedAt, String correlationId) {
        this.auditId = auditId;
        this.assessmentId = assessmentId;
        this.declarationId = declarationId;
        this.declarationVersion = declarationVersion;
        this.layoutVersion = layoutVersion;
        this.layoutDigest = layoutDigest;
        this.ruleSnapshotId = ruleSnapshotId;
        this.validationId = validationId;
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.notes = notes;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt != null ? decidedAt : Instant.now();
        this.correlationId = correlationId;
    }

    public String getAuditId() { return auditId; }
    public void setAuditId(String auditId) { this.auditId = auditId; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public String getDeclarationId() { return declarationId; }
    public void setDeclarationId(String declarationId) { this.declarationId = declarationId; }

    public int getDeclarationVersion() { return declarationVersion; }
    public void setDeclarationVersion(int declarationVersion) { this.declarationVersion = declarationVersion; }

    public int getLayoutVersion() { return layoutVersion; }
    public void setLayoutVersion(int layoutVersion) { this.layoutVersion = layoutVersion; }

    public String getLayoutDigest() { return layoutDigest; }
    public void setLayoutDigest(String layoutDigest) { this.layoutDigest = layoutDigest; }

    public String getRuleSnapshotId() { return ruleSnapshotId; }
    public void setRuleSnapshotId(String ruleSnapshotId) { this.ruleSnapshotId = ruleSnapshotId; }

    public String getValidationId() { return validationId; }
    public void setValidationId(String validationId) { this.validationId = validationId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}
