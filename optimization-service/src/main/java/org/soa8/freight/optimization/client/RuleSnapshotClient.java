package org.soa8.freight.optimization.client;

import org.soa8.freight.common.model.CompatibilityRuleDto;
import org.soa8.freight.common.model.RuleSnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Client to resolve immutable Rule Snapshot from compatibility-rule-service or fallback to authoritative seed rules.
 */
@Component
public class RuleSnapshotClient {

    private static final Logger log = LoggerFactory.getLogger(RuleSnapshotClient.class);

    private final RestTemplate restTemplate;
    private final String rulesServiceBaseUrl;
    private final boolean allowTestFallback;

    public RuleSnapshotClient(RestTemplateBuilder builder,
                              @Value("${services.rules.url:http://localhost:8083}") String rulesServiceBaseUrl,
                              @Value("${optimization.rules.allow-test-fallback:false}") boolean allowTestFallback) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(1500))
                .setReadTimeout(Duration.ofMillis(2500))
                .build();
        this.rulesServiceBaseUrl = rulesServiceBaseUrl;
        this.allowTestFallback = allowTestFallback;
    }

    /**
     * Resolve the exact immutable rule snapshot from compatibility-rule-service.
     * Fails closed if the service is unreachable, snapshot is missing, or returned snapshot ID does not match.
     */
    public Optional<RuleSnapshotDto> resolveSnapshot(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }

        try {
            String url = rulesServiceBaseUrl + "/api/v1/rule-snapshots/" + snapshotId.trim();
            RuleSnapshotDto snapshot = restTemplate.getForObject(url, RuleSnapshotDto.class);
            if (snapshot != null) {
                // Reject snapshot ID mismatch
                if (!snapshotId.trim().equalsIgnoreCase(snapshot.getSnapshotId())) {
                    log.error("Retrieved snapshot ID '{}' does not match requested snapshot ID '{}'. Rejecting for safety.",
                            snapshot.getSnapshotId(), snapshotId);
                    return Optional.empty();
                }
                return Optional.of(snapshot);
            }
        } catch (Exception e) {
            log.warn("Could not retrieve rule snapshot '{}' from {}: {}.",
                    snapshotId, rulesServiceBaseUrl, e.getMessage());
        }

        // Allow test fallback ONLY when explicitly enabled via configuration (e.g. in test fixtures)
        if (allowTestFallback && "snap-2026-q4-v1".equalsIgnoreCase(snapshotId)) {
            log.info("Using explicitly enabled test fallback snapshot for '{}'", snapshotId);
            return Optional.of(getAuthoritativeDefaultSnapshot());
        }

        return Optional.empty();
    }

    public static RuleSnapshotDto getAuthoritativeDefaultSnapshot() {
        CompatibilityRuleDto rule1 = new CompatibilityRuleDto(
                "rule-cmv-001",
                "CMV-HAZ-1-3",
                "1", // Explosives
                "3", // Flammable Liquids
                "SEPARATED_BY_DISTANCE",
                3000,
                "Explosives and Flammable Liquids must be segregated by at least 3000 mm",
                "INDIA_CMV_RULES"
        );

        CompatibilityRuleDto rule2 = new CompatibilityRuleDto(
                "rule-cmv-002",
                "CMV-HAZ-1-2.1",
                "1", // Explosives
                "2.1", // Flammable Gases
                "FORBIDDEN",
                0,
                "Co-loading Explosives and Flammable Gases in the same vehicle is strictly prohibited",
                "INDIA_CMV_RULES"
        );

        CompatibilityRuleDto rule3 = new CompatibilityRuleDto(
                "rule-cmv-003",
                "CMV-HAZ-4.3-8",
                "4.3", // Dangerous when wet
                "8",   // Corrosives (liquids)
                "SEPARATED_BY_DISTANCE",
                2000,
                "Substances dangerous when wet must not be stowed near liquid corrosives",
                "INDIA_CMV_RULES"
        );

        return new RuleSnapshotDto(
                "snap-2026-q4-v1",
                "INDIA_CMV_RULES",
                java.time.Instant.parse("2026-10-01T00:00:00Z"),
                "PUBLISHED",
                "0f89d3112cf3072236fa7830f023774619d8033282eb10e4a6a5704d9a2ff2a4",
                List.of(rule1, rule2, rule3)
        );
    }
}
