package org.soa8.freight.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.soa8.freight.common.model.CompatibilityRuleDto;
import org.soa8.freight.common.model.RuleSnapshotDto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Utility for computing canonical SHA-256 digests for rule snapshots and candidate layouts.
 *
 * Implements deterministic canonical serialization of compatibility rules:
 * - Stable sorting of rules by ruleCode, primaryHazardClass, secondaryHazardClass, segregationType, minDistanceMm
 * - Normalization of jurisdiction and segregation type strings
 * - Exclusion of volatile runtime metadata (e.g., dynamic timestamps) to guarantee reproducibility
 */
public final class RuleSnapshotDigestUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private RuleSnapshotDigestUtils() {}

    /**
     * Compute canonical SHA-256 hex digest for an immutable RuleSnapshotDto and its constituent rules.
     */
    public static String computeCanonicalDigest(RuleSnapshotDto snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot cannot be null for digest computation");
        }
        return computeCanonicalDigest(snapshot.getSnapshotId(), snapshot.getJurisdiction(), snapshot.getRules());
    }

    /**
     * Compute canonical SHA-256 hex digest given snapshot ID, jurisdiction, and rule list.
     */
    public static String computeCanonicalDigest(String snapshotId, String jurisdiction, List<CompatibilityRuleDto> rules) {
        Map<String, Object> canonicalPayload = new LinkedHashMap<>();
        canonicalPayload.put("snapshotId", snapshotId != null ? snapshotId.trim() : "");
        canonicalPayload.put("jurisdiction", jurisdiction != null ? jurisdiction.trim().toUpperCase(Locale.ROOT) : "");

        List<Map<String, Object>> sortedRules = new ArrayList<>();
        if (rules != null) {
            for (CompatibilityRuleDto rule : rules) {
                if (rule == null) continue;
                Map<String, Object> ruleMap = new LinkedHashMap<>();
                ruleMap.put("ruleCode", rule.getRuleCode() != null ? rule.getRuleCode().trim() : "");
                ruleMap.put("primaryHazardClass", rule.getPrimaryHazardClass() != null ? rule.getPrimaryHazardClass().trim() : "");
                ruleMap.put("secondaryHazardClass", rule.getSecondaryHazardClass() != null ? rule.getSecondaryHazardClass().trim() : "");
                ruleMap.put("segregationType", rule.getSegregationType() != null ? rule.getSegregationType().trim().toUpperCase(Locale.ROOT) : "");
                ruleMap.put("minDistanceMm", rule.getMinDistanceMm());
                sortedRules.add(ruleMap);
            }
        }

        // Sort rules deterministically
        sortedRules.sort((r1, r2) -> {
            int c1 = Objects.toString(r1.get("ruleCode"), "").compareTo(Objects.toString(r2.get("ruleCode"), ""));
            if (c1 != 0) return c1;
            int c2 = Objects.toString(r1.get("primaryHazardClass"), "").compareTo(Objects.toString(r2.get("primaryHazardClass"), ""));
            if (c2 != 0) return c2;
            int c3 = Objects.toString(r1.get("secondaryHazardClass"), "").compareTo(Objects.toString(r2.get("secondaryHazardClass"), ""));
            if (c3 != 0) return c3;
            int c4 = Objects.toString(r1.get("segregationType"), "").compareTo(Objects.toString(r2.get("segregationType"), ""));
            if (c4 != 0) return c4;
            return Integer.compare((Integer) r1.get("minDistanceMm"), (Integer) r2.get("minDistanceMm"));
        });

        canonicalPayload.put("rules", sortedRules);

        try {
            String json = OBJECT_MAPPER.writeValueAsString(canonicalPayload);
            return DigestUtils.sha256Hex(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to canonically serialize rule snapshot", e);
        }
    }
}
