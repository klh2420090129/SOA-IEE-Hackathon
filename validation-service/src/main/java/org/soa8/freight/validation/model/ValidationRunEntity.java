package org.soa8.freight.validation.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "validation_runs")
public class ValidationRunEntity {
    @Id
    private String validationId;

    private String candidateLayoutId;
    private int candidateLayoutVersion;
    private String candidateContentDigest;
    private String ruleSnapshotId;
    private String ruleSnapshotDigest;
    private boolean passed;
    private String validatorVersion;
    private int findingsCount;

    @Lob
    private String findingsJson;

    private Instant validatedAt;

    public ValidationRunEntity() {}

    public ValidationRunEntity(String validationId, String candidateLayoutId, int candidateLayoutVersion,
                               String candidateContentDigest, String ruleSnapshotId, String ruleSnapshotDigest,
                               boolean passed, String validatorVersion,
                               int findingsCount, String findingsJson) {
        this.validationId = validationId;
        this.candidateLayoutId = candidateLayoutId;
        this.candidateLayoutVersion = candidateLayoutVersion;
        this.candidateContentDigest = candidateContentDigest;
        this.ruleSnapshotId = ruleSnapshotId;
        this.ruleSnapshotDigest = ruleSnapshotDigest;
        this.passed = passed;
        this.validatorVersion = validatorVersion;
        this.findingsCount = findingsCount;
        this.findingsJson = findingsJson;
        this.validatedAt = Instant.now();
    }

    public String getValidationId() { return validationId; }
    public void setValidationId(String validationId) { this.validationId = validationId; }

    public String getCandidateLayoutId() { return candidateLayoutId; }
    public void setCandidateLayoutId(String candidateLayoutId) { this.candidateLayoutId = candidateLayoutId; }

    public int getCandidateLayoutVersion() { return candidateLayoutVersion; }
    public void setCandidateLayoutVersion(int candidateLayoutVersion) { this.candidateLayoutVersion = candidateLayoutVersion; }

    public String getCandidateContentDigest() { return candidateContentDigest; }
    public void setCandidateContentDigest(String candidateContentDigest) { this.candidateContentDigest = candidateContentDigest; }

    public String getRuleSnapshotId() { return ruleSnapshotId; }
    public void setRuleSnapshotId(String ruleSnapshotId) { this.ruleSnapshotId = ruleSnapshotId; }

    public String getRuleSnapshotDigest() { return ruleSnapshotDigest; }
    public void setRuleSnapshotDigest(String ruleSnapshotDigest) { this.ruleSnapshotDigest = ruleSnapshotDigest; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getValidatorVersion() { return validatorVersion; }
    public void setValidatorVersion(String validatorVersion) { this.validatorVersion = validatorVersion; }

    public int getFindingsCount() { return findingsCount; }
    public void setFindingsCount(int findingsCount) { this.findingsCount = findingsCount; }

    public String getFindingsJson() { return findingsJson; }
    public void setFindingsJson(String findingsJson) { this.findingsJson = findingsJson; }

    public Instant getValidatedAt() { return validatedAt; }
    public void setValidatedAt(Instant validatedAt) { this.validatedAt = validatedAt; }
}
