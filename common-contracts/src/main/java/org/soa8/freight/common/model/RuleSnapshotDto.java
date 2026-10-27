package org.soa8.freight.common.model;

import java.time.Instant;
import java.util.List;

public class RuleSnapshotDto {
    private String snapshotId;
    private String jurisdiction;
    private Instant effectiveDate;
    private String status; // PUBLISHED, DRAFT
    private String snapshotDigest;
    private List<CompatibilityRuleDto> rules;

    public RuleSnapshotDto() {
    }

    public RuleSnapshotDto(String snapshotId, String jurisdiction, Instant effectiveDate, String status,
                           String snapshotDigest, List<CompatibilityRuleDto> rules) {
        this.snapshotId = snapshotId;
        this.jurisdiction = jurisdiction;
        this.effectiveDate = effectiveDate;
        this.status = status;
        this.snapshotDigest = snapshotDigest;
        this.rules = rules;
    }

    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public Instant getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Instant effectiveDate) { this.effectiveDate = effectiveDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSnapshotDigest() { return snapshotDigest; }
    public void setSnapshotDigest(String snapshotDigest) { this.snapshotDigest = snapshotDigest; }

    public List<CompatibilityRuleDto> getRules() { return rules; }
    public void setRules(List<CompatibilityRuleDto> rules) { this.rules = rules; }
}
