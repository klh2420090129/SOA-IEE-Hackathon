package org.soa8.freight.rules.config;

import org.soa8.freight.common.util.DigestUtils;
import org.soa8.freight.rules.model.CompatibilityRuleEntity;
import org.soa8.freight.rules.model.RuleSnapshotEntity;
import org.soa8.freight.rules.repository.CompatibilityRuleRepository;
import org.soa8.freight.rules.repository.RuleSnapshotRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;

@Configuration
public class SeedRulesConfig {

    @Bean
    public CommandLineRunner initRules(CompatibilityRuleRepository ruleRepository,
                                       RuleSnapshotRepository snapshotRepository) {
        return args -> {
            if (ruleRepository.count() == 0) {
                // Seed structured compatibility rules
                CompatibilityRuleEntity rule1 = new CompatibilityRuleEntity(
                        "rule-cmv-001",
                        "CMV-HAZ-1-3",
                        "1", // Explosives
                        "3", // Flammable Liquids
                        "SEPARATED_BY_DISTANCE",
                        3000, // 3000 mm minimum separation
                        "Explosives and Flammable Liquids must be segregated by at least 3000 mm",
                        "INDIA_CMV_RULES"
                );

                CompatibilityRuleEntity rule2 = new CompatibilityRuleEntity(
                        "rule-cmv-002",
                        "CMV-HAZ-1-2.1",
                        "1", // Explosives
                        "2.1", // Flammable Gases
                        "FORBIDDEN",
                        0,
                        "Co-loading Explosives and Flammable Gases in the same vehicle is strictly prohibited",
                        "INDIA_CMV_RULES"
                );

                CompatibilityRuleEntity rule3 = new CompatibilityRuleEntity(
                        "rule-cmv-003",
                        "CMV-HAZ-4.3-8",
                        "4.3", // Dangerous when wet
                        "8",   // Corrosives (liquids)
                        "SEPARATED_BY_DISTANCE",
                        2000,
                        "Substances dangerous when wet must not be stowed near liquid corrosives",
                        "INDIA_CMV_RULES"
                );

                ruleRepository.saveAll(List.of(rule1, rule2, rule3));

                // Publish initial snapshot
                String digest = DigestUtils.sha256Hex("INDIA_CMV_RULES-2026-Q4-SNAPSHOT");
                RuleSnapshotEntity snapshot = new RuleSnapshotEntity(
                        "snap-2026-q4-v1",
                        "INDIA_CMV_RULES",
                        Instant.now(),
                        "PUBLISHED",
                        digest,
                        List.of(rule1, rule2, rule3)
                );
                snapshotRepository.save(snapshot);
            }
        };
    }
}
