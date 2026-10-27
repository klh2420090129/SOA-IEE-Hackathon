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

        // Width change
        ItemPlacementDto widthChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1200, 801, 1000, 500.0,
                "DEFAULT", true, 1500.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(widthChange))).isNotEqualTo(baseDigest);

        // Height change
        ItemPlacementDto heightChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1200, 800, 1001, 500.0,
                "DEFAULT", true, 1500.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(heightChange))).isNotEqualTo(baseDigest);

        // UN Number change
        ItemPlacementDto unNumberChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1203",
                0, 0, 0, 1200, 800, 1000, 500.0,
                "DEFAULT", true, 1500.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(unNumberChange))).isNotEqualTo(baseDigest);

        // Stackability change
        ItemPlacementDto stackableChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1200, 800, 1000, 500.0,
                "DEFAULT", false, 1500.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(stackableChange))).isNotEqualTo(baseDigest);

        // Max stack weight change
        ItemPlacementDto maxStackWeightChange = new ItemPlacementDto(
                "pl-1", "item-1", 0, "3", "UN1993",
                0, 0, 0, 1200, 800, 1000, 500.0,
                "DEFAULT", true, 1600.0
        );
        assertThat(CandidateDigestUtils.computeCanonicalDigest(List.of(maxStackWeightChange))).isNotEqualTo(baseDigest);
    }

    @Test
    @DisplayName("Ordering semantics: List ordering is preserved deterministically; reordered placements produce distinct digests")
    void testPlacementListOrderingSemantics() {
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

        String order1 = CandidateDigestUtils.computeCanonicalDigest(List.of(p1, p2));
        String order2 = CandidateDigestUtils.computeCanonicalDigest(List.of(p2, p1));

        assertThat(order1).isNotBlank();
        assertThat(order2).isNotBlank();
        // Placements are serialized in their explicit candidate sequence
        assertThat(order1).isNotEqualTo(order2);
    }

    @Test
    @DisplayName("Explicit null/missing-field canonicalization: missing fields default deterministically")
    void testExplicitNullAndMissingFieldCanonicalization() {
        ItemPlacementDto nullFields = new ItemPlacementDto();
        // Set only required non-null coordinate/dimension primitives
        nullFields.setPlacementId(null);
        nullFields.setCargoItemId(null);
        nullFields.setHazardClass(null);
        nullFields.setUnNumber(null);
        nullFields.setOrientation(null);
        nullFields.setStackable(null);
        nullFields.setMaxStackWeightKg(null);

        String digestWithNulls = CandidateDigestUtils.computeCanonicalDigest(List.of(nullFields));
        assertThat(digestWithNulls).isNotBlank();

        // Compare with explicitly set default equivalents
        ItemPlacementDto explicitDefaults = new ItemPlacementDto();
        explicitDefaults.setPlacementId("");
        explicitDefaults.setCargoItemId("");
        explicitDefaults.setHazardClass("");
        explicitDefaults.setUnNumber("");
        explicitDefaults.setOrientation("DEFAULT");
        explicitDefaults.setStackable(Boolean.FALSE);
        explicitDefaults.setMaxStackWeightKg(null);

        String digestWithDefaults = CandidateDigestUtils.computeCanonicalDigest(List.of(explicitDefaults));
        assertThat(digestWithNulls).isEqualTo(digestWithDefaults);
    }
}
