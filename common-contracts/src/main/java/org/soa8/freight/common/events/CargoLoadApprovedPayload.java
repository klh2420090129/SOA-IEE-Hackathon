package org.soa8.freight.common.events;

public class CargoLoadApprovedPayload {
    private String assessmentId;
    private String declarationId;
    private int declarationVersion;
    private int layoutVersion;
    private String layoutDigest;
    private String ruleSnapshotId;
    private String validationId;
    private String approvedBy;
    private String approvalNotes;

    public CargoLoadApprovedPayload() {
    }

    public CargoLoadApprovedPayload(String assessmentId, String declarationId, int declarationVersion,
                                   int layoutVersion, String layoutDigest, String ruleSnapshotId,
                                   String validationId, String approvedBy, String approvalNotes) {
        this.assessmentId = assessmentId;
        this.declarationId = declarationId;
        this.declarationVersion = declarationVersion;
        this.layoutVersion = layoutVersion;
        this.layoutDigest = layoutDigest;
        this.ruleSnapshotId = ruleSnapshotId;
        this.validationId = validationId;
        this.approvedBy = approvedBy;
        this.approvalNotes = approvalNotes;
    }

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

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getApprovalNotes() { return approvalNotes; }
    public void setApprovalNotes(String approvalNotes) { this.approvalNotes = approvalNotes; }
}
