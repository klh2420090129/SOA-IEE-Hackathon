package org.soa8.freight.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.CompatibilityRuleDto;
import org.soa8.freight.common.model.RuleSnapshotDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleSnapshotDigestUtilsTest {

    @Test
    @DisplayName("Should produce identical digest for identical semantic snapshots despite ordering or volatile timestamps")
    void shouldProduceIdenticalDigestDeterministically() {
        CompatibilityRuleDto ruleA = new CompatibilityRuleDto(
                "r-1", "CMV-HAZ-1-3", "1", "3", "SEPARATED_BY_DISTANCE", 3000, "Rule 1", "INDIA"
        );
        CompatibilityRuleDto ruleB = new CompatibilityRuleDto(
                "r-2", "CMV-HAZ-1-2.1", "1", "2.1", "FORBIDDEN", 0, "Rule 2", "INDIA"
        );

        RuleSnapshotDto snapshot1 = new RuleSnapshotDto(
                "snap-1", "INDIA", Instant.parse("2026-10-01T00:00:00Z"), "PUBLISHED", null, List.of(ruleA, ruleB)
        );

        // Snapshot 2 has reversed rule order and different effective timestamp, but identical rule semantics
        RuleSnapshotDto snapshot2 = new RuleSnapshotDto(
                "snap-1", "INDIA", Instant.parse("2026-10-27T12:00:00Z"), "PUBLISHED", null, List.of(ruleB, ruleA)
        );

        String digest1 = RuleSnapshotDigestUtils.computeCanonicalDigest(snapshot1);
        String digest2 = RuleSnapshotDigestUtils.computeCanonicalDigest(snapshot2);

        assertThat(digest1).isNotBlank();
        assertThat(digest1).isEqualTo(digest2);
    }

    @Test
    @DisplayName("Should produce different digest when rule distance or semantics change")
    void shouldChangeDigestWhenRuleChanges() {
        CompatibilityRuleDto ruleBase = new CompatibilityRuleDto(
                "r-1", "CMV-HAZ-1-3", "1", "3", "SEPARATED_BY_DISTANCE", 3000, "Rule 1", "INDIA"
        );
        CompatibilityRuleDto ruleModified = new CompatibilityRuleDto(
                "r-1", "CMV-HAZ-1-3", "1", "3", "SEPARATED_BY_DISTANCE", 3500, "Rule 1 with 3500mm", "INDIA"
        );

        RuleSnapshotDto snapshot1 = new RuleSnapshotDto(
                "snap-1", "INDIA", Instant.now(), "PUBLISHED", null, List.of(ruleBase)
        );
        RuleSnapshotDto snapshot2 = new RuleSnapshotDto(
                "snap-1", "INDIA", Instant.now(), "PUBLISHED", null, List.of(ruleModified)
        );

        String digest1 = RuleSnapshotDigestUtils.computeCanonicalDigest(snapshot1);
        String digest2 = RuleSnapshotDigestUtils.computeCanonicalDigest(snapshot2);

        assertThat(digest1).isNotEqualTo(digest2);
    }

    @Test
    @DisplayName("Should reject null snapshot input")
    void shouldRejectNullSnapshot() {
        assertThatThrownBy(() -> RuleSnapshotDigestUtils.computeCanonicalDigest(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
