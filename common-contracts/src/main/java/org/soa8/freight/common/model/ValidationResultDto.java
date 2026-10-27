package org.soa8.freight.common.model;

import java.util.List;

public class ValidationResultDto {
    private String validationId;
    private boolean passed;
    private String validatorVersion;
    private int evaluatedRulesCount;
    private List<ValidationFindingDto> findings;
    private String candidateLayoutId;
    private int candidateLayoutVersion;
    private String candidateContentDigest;
    private String ruleSnapshotId;
    private String ruleSnapshotDigest;

    public ValidationResultDto() {
    }

    public ValidationResultDto(String validationId, boolean passed, String validatorVersion, int evaluatedRulesCount, List<ValidationFindingDto> findings) {
        this.validationId = validationId;
        this.passed = passed;
        this.validatorVersion = validatorVersion;
        this.evaluatedRulesCount = evaluatedRulesCount;
        this.findings = findings;
    }

    public String getValidationId() { return validationId; }
    public void setValidationId(String validationId) { this.validationId = validationId; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getValidatorVersion() { return validatorVersion; }
    public void setValidatorVersion(String validatorVersion) { this.validatorVersion = validatorVersion; }

    public int getEvaluatedRulesCount() { return evaluatedRulesCount; }
    public void setEvaluatedRulesCount(int evaluatedRulesCount) { this.evaluatedRulesCount = evaluatedRulesCount; }

    public List<ValidationFindingDto> getFindings() { return findings; }
    public void setFindings(List<ValidationFindingDto> findings) { this.findings = findings; }

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
}
