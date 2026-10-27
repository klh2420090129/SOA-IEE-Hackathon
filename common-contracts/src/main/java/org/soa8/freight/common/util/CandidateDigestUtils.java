package org.soa8.freight.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.soa8.freight.common.model.ItemPlacementDto;

import java.util.*;

/**
 * Utility for computing canonical SHA-256 digests for candidate layouts and item placements.
 *
 * Guarantees cross-language parity between Java and Python:
 * - Stable, canonical attribute keys sorted alphabetically
 * - Deterministic serialization of nulls, booleans, integers, and floats
 * - Byte-for-byte SHA-256 identical to Python worker's compute_content_digest
 */
public final class CandidateDigestUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private CandidateDigestUtils() {}

    /**
     * Compute canonical SHA-256 hex digest for a list of ItemPlacementDto.
     */
    public static String computeCanonicalDigest(List<ItemPlacementDto> placements) {
        if (placements == null || placements.isEmpty()) {
            return DigestUtils.sha256Hex("[]");
        }

        List<Map<String, Object>> canonicalList = new ArrayList<>(placements.size());
        for (ItemPlacementDto p : placements) {
            if (p == null) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("cargoItemId", p.getCargoItemId() != null ? p.getCargoItemId() : "");
            map.put("hazardClass", p.getHazardClass() != null ? p.getHazardClass() : "");
            map.put("heightMm", p.getHeightMm());
            map.put("instanceIndex", p.getInstanceIndex());
            map.put("lengthMm", p.getLengthMm());
            map.put("maxStackWeightKg", p.getMaxStackWeightKg());
            map.put("orientation", p.getOrientation() != null ? p.getOrientation() : "DEFAULT");
            map.put("placementId", p.getPlacementId() != null ? p.getPlacementId() : "");
            map.put("stackable", p.getStackable() != null ? p.getStackable() : Boolean.FALSE);
            map.put("unNumber", p.getUnNumber() != null ? p.getUnNumber() : "");
            map.put("weightKg", p.getWeightKg());
            map.put("widthMm", p.getWidthMm());
            map.put("xMm", p.getXMm());
            map.put("yMm", p.getYMm());
            map.put("zMm", p.getZMm());
            canonicalList.add(map);
        }

        try {
            String json = OBJECT_MAPPER.writeValueAsString(canonicalList);
            return DigestUtils.sha256Hex(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to canonically serialize candidate placements", e);
        }
    }
}
