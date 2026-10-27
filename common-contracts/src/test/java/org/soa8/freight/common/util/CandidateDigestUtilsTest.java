package org.soa8.freight.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.ItemPlacementDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateDigestUtilsTest {

    @Test
    @DisplayName("Cross-language parity: Empty placements must match SHA-256 of empty JSON array")
    void testEmptyPlacementsDigest() {
        String emptyDigest = CandidateDigestUtils.computeCanonicalDigest(Collections.emptyList());
        assertThat(emptyDigest).isEqualTo("4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945");

        String nullDigest = CandidateDigestUtils.computeCanonicalDigest(null);
        assertThat(nullDigest).isEqualTo("4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945");
    }

    @Test
    @DisplayName("Cross-language parity: Single placement byte-for-byte matches Python worker")
    void testSinglePlacementDigestParity() {
        ItemPlacementDto p1 = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1200, 800, 1000, 500.0,
                "DEFAULT", true, 1500.0
        );

        String digest = CandidateDigestUtils.computeCanonicalDigest(List.of(p1));
        assertThat(digest).isEqualTo("e45ded0b55dd2051095602375f842716d8dabe20955bb9d26e3429a45d44690f");
    }

    @Test
    @DisplayName("Cross-language parity: Multiple placements with null/optional fields match Python worker")
    void testMultiplePlacementsDigestParity() {
        ItemPlacementDto p1 = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1200, 800, 1000, 500.0,
                "DEFAULT", true, 1500.0
        );
        ItemPlacementDto p2 = new ItemPlacementDto(
                "pl-2", "item-2", 1, "8", "UN1789",
                1500, 500, 0, 1000, 1000, 800, 350.25,
                "ROTATE_YAW_90", false, null
        );

        String digest = CandidateDigestUtils.computeCanonicalDigest(List.of(p1, p2));
        assertThat(digest).isEqualTo("c37d14aad45e2ad525a7761a4f5b2dc4c25ded8ba3c612bf51cd1ba68e69cb4d");
    }

    @Test
    @DisplayName("Regression: E5 live workflow placement fixture matches 129175d59fa95276657ee3c6beadf74a82a4a8c46ec60e2ad756a70f3a3fb7a9")
    void testE5LiveWorkflowPlacementDigestFixture() {
        ItemPlacementDto p1 = new ItemPlacementDto(
                "pl-item-e5-a-0-1", "item-e5-a", 0, "3", "UN1993",
                0, 0, 0, 1200, 800, 1000, 400.0,
                "DEFAULT", true, 1200.0
        );
        ItemPlacementDto p2 = new ItemPlacementDto(
                "pl-item-e5-b-0-2", "item-e5-b", 0, "8", "UN1789",
                0, 800, 0, 1000, 800, 900, 350.0,
                "DEFAULT", false, 0.0
        );

        String digest = CandidateDigestUtils.computeCanonicalDigest(List.of(p1, p2));
        assertThat(digest).isEqualTo("129175d59fa95276657ee3c6beadf74a82a4a8c46ec60e2ad756a70f3a3fb7a9");
    }

    @Test
    @DisplayName("Safety sensitivity: Any change to coordinates, dimensions, weight, or orientation changes the digest")
    void testDigestSensitivityToSafetyFields() {
        ItemPlacementDto base = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1200, 800, 1000, 500.0,
                "DEFAULT", true, 1500.0
        );
        String baseDigest = CandidateDigestUtils.computeCanonicalDigest(List.of(base));

        // Coordinate change
        ItemPlacementDto coordChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                10, 0, 0, 1200, 800, 1000, 500.0,
                "DEFAULT", true, 1500.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(coordChange))).isNotEqualTo(baseDigest);

        // Dimension change
        ItemPlacementDto dimChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1201, 800, 1000, 500.0,
                "DEFAULT", true, 1500.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(dimChange))).isNotEqualTo(baseDigest);

        // Weight change
        ItemPlacementDto weightChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1200, 800, 1000, 500.5,
                "DEFAULT", true, 1500.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(weightChange))).isNotEqualTo(baseDigest);

        // Orientation change
        ItemPlacementDto orientChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1200, 800, 1000, 500.0,
                "ROTATE_YAW_90", true, 1500.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(orientChange))).isNotEqualTo(baseDigest);

        // Hazard class change
        ItemPlacementDto hazardChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "8", "UN1993",
                0, 0, 0, 1200, 800, 1000, 500.0,
                "DEFAULT", true, 1500.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(hazardChange))).isNotEqualTo(baseDigest);
    }
}
