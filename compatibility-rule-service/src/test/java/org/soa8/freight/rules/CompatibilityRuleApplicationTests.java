package org.soa8.freight.rules;

import org.junit.jupiter.api.Test;
import org.soa8.freight.rules.repository.CompatibilityRuleRepository;
import org.soa8.freight.rules.repository.RuleSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "eureka.client.enabled=false"
})
class CompatibilityRuleApplicationTests {

    @Autowired
    private CompatibilityRuleRepository ruleRepository;

    @Autowired
    private RuleSnapshotRepository snapshotRepository;

    @Test
    void shouldSeedRulesAndSnapshot() {
        assertThat(ruleRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(snapshotRepository.findById("snap-2026-q4-v1")).isPresent();
    }
}
