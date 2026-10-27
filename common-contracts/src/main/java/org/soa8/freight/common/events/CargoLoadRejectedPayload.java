package org.soa8.freight.common.events;

public class CargoLoadRejectedPayload {
    private String assessmentId;
    private String declarationId;
    private int declarationVersion;
    private int layoutVersion;
    private String layoutDigest;
    private String ruleSnapshotId;
    private String rejectedBy;
    private String rejectionReasonCode;
    private String rejectionNotes;

    public CargoLoadRejectedPayload() {
    }

    public CargoLoadRejectedPayload(String assessmentId, String declarationId, int declarationVersion,
                                   int layoutVersion, String layoutDigest, String ruleSnapshotId,
                                   String rejectedBy, String rejectionReasonCode, String rejectionNotes) {
        this.assessmentId = assessmentId;
        this.declarationId = declarationId;
        this.declarationVersion = declarationVersion;
        this.layoutVersion = layoutVersion;
        this.layoutDigest = layoutDigest;
        this.ruleSnapshotId = ruleSnapshotId;
        this.rejectedBy = rejectedBy;
        this.rejectionReasonCode = rejectionReasonCode;
        this.rejectionNotes = rejectionNotes;
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

    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }

    public String getRejectionReasonCode() { return rejectionReasonCode; }
    public void setRejectionReasonCode(String rejectionReasonCode) { this.rejectionReasonCode = rejectionReasonCode; }

    public String getRejectionNotes() { return rejectionNotes; }
    public void setRejectionNotes(String rejectionNotes) { this.rejectionNotes = rejectionNotes; }
}
