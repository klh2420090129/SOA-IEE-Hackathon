package org.soa8.freight.declaration.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "declaration_workflow_projections")
public class DeclarationWorkflowProjection {
    @Id
    private String declarationId;

    private String assessmentId;
    private String workflowStatus; // PENDING, OPTIMIZING, APPROVED, REJECTED
    private String rejectionReasonCode;
    private String rejectionNotes;
    private Instant lastUpdated;

    public DeclarationWorkflowProjection() {}

    public DeclarationWorkflowProjection(String declarationId, String assessmentId, String workflowStatus,
                                         String rejectionReasonCode, String rejectionNotes) {
        this.declarationId = declarationId;
        this.assessmentId = assessmentId;
        this.workflowStatus = workflowStatus;
        this.rejectionReasonCode = rejectionReasonCode;
        this.rejectionNotes = rejectionNotes;
        this.lastUpdated = Instant.now();
    }

    public String getDeclarationId() { return declarationId; }
    public void setDeclarationId(String declarationId) { this.declarationId = declarationId; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public String getWorkflowStatus() { return workflowStatus; }
    public void setWorkflowStatus(String workflowStatus) { this.workflowStatus = workflowStatus; }

    public String getRejectionReasonCode() { return rejectionReasonCode; }
    public void setRejectionReasonCode(String rejectionReasonCode) { this.rejectionReasonCode = rejectionReasonCode; }

    public String getRejectionNotes() { return rejectionNotes; }
    public void setRejectionNotes(String rejectionNotes) { this.rejectionNotes = rejectionNotes; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}
