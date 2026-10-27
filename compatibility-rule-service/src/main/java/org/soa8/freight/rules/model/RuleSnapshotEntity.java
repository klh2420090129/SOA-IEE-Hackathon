package org.soa8.freight.rules.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rule_snapshots")
public class RuleSnapshotEntity {
    @Id
    private String snapshotId;

    private String jurisdiction;
    private Instant effectiveDate;
    private String status; // PUBLISHED, DRAFT
    private String snapshotDigest;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "snapshot_rule_mappings",
        joinColumns = @JoinColumn(name = "snapshot_id"),
        inverseJoinColumns = @JoinColumn(name = "rule_id")
    )
    private List<CompatibilityRuleEntity> rules = new ArrayList<>();

    public RuleSnapshotEntity() {}

    public RuleSnapshotEntity(String snapshotId, String jurisdiction, Instant effectiveDate,
                              String status, String snapshotDigest, List<CompatibilityRuleEntity> rules) {
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

    public List<CompatibilityRuleEntity> getRules() { return rules; }
    public void setRules(List<CompatibilityRuleEntity> rules) { this.rules = rules; }
}
