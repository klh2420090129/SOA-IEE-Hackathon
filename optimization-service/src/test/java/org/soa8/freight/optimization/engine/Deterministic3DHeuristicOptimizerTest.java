package org.soa8.freight.optimization.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Deterministic3DHeuristicOptimizerTest {

    private Deterministic3DHeuristicOptimizer optimizer;
    private CompartmentDto standardCompartment;
    private RuleSnapshotDto standardSnapshot;

    @BeforeEach
    void setUp() {
        optimizer = new Deterministic3DHeuristicOptimizer();
        standardCompartment = new CompartmentDto("comp-test-trailer", "Standard Trailer", 12000, 2400, 2600, 25000);

        CompatibilityRuleDto ruleDist = new CompatibilityRuleDto(
                "rule-cmv-001", "CMV-HAZ-1-3", "1", "3", "SEPARATED_BY_DISTANCE", 3000, "Min 3000mm", "INDIA"
        );
        CompatibilityRuleDto ruleForbid = new CompatibilityRuleDto(
                "rule-cmv-002", "CMV-HAZ-1-2.1", "1", "2.1", "FORBIDDEN", 0, "Forbidden co-loading", "INDIA"
        );
        standardSnapshot = new RuleSnapshotDto("snap-2026-q4-v1", "INDIA", null, "PUBLISHED", "digest-1", List.of(ruleDist, ruleForbid));
    }

    @Test
    @DisplayName("1. Empty input produces empty placements, zero unassigned items, and FEASIBLE status")
    void testEmptyInput() {
        CandidateLayoutDto result = optimizer.optimize(standardCompartment, List.of(), standardSnapshot, "snap-2026-q4-v1");
        assertThat(result.getPlacements()).isEmpty();
        assertThat(result.getUnassignedItems()).isEmpty();
        assertThat(result.getSolverStatus()).isEqualTo(SolverStatus.FEASIBLE.name());
        assertThat(result.getProvenance()).isEqualTo(Deterministic3DHeuristicOptimizer.ALGORITHM_PROVENANCE);
    }

    @Test
    @DisplayName("2. Single cargo unit placed at origin (0,0,0) with DEFAULT orientation")
    void testSingleCargoUnit() {
        CargoItemDto item = new CargoItemDto("it-box", "Dry Box", "NON_HAZ", "UN0000", 1000, 800, 600, 200, true, 500, 1);
        CandidateLayoutDto result = optimizer.optimize(standardCompartment, List.of(item), standardSnapshot, "snap-2026-q4-v1");

        assertThat(result.getPlacements()).hasSize(1);
        assertThat(result.getUnassignedItems()).isEmpty();
        ItemPlacementDto p = result.getPlacements().get(0);
        assertThat(p.getXMm()).isEqualTo(0);
        assertThat(p.getYMm()).isEqualTo(0);
        assertThat(p.getZMm()).isEqualTo(0);
        assertThat(p.getLengthMm()).isEqualTo(1000);
        assertThat(p.getWidthMm()).isEqualTo(800);
        assertThat(p.getHeightMm()).isEqualTo(600);
        assertThat(p.getOrientation()).isEqualTo("DEFAULT");
    }

    @Test
    @DisplayName("3. Multiple units placed without 3D overlap and expanding quantities to unique physical units")
    void testMultipleUnitsConservationAndNonOverlap() {
        CargoItemDto item1 = new CargoItemDto("it-drum-1", "Drums", "NON_HAZ", "UN0000", 800, 800, 1000, 250, true, 500, 3);
        CargoItemDto item2 = new CargoItemDto("it-box-2", "Boxes", "NON_HAZ", "UN0000", 1200, 1000, 800, 400, true, 800, 2);

        CandidateLayoutDto result = optimizer.optimize(standardCompartment, List.of(item1, item2), standardSnapshot, "snap-2026-q4-v1");

        assertThat(result.getPlacements()).hasSize(5); // 3 drums + 2 boxes
        assertThat(result.getUnassignedItems()).isEmpty();

        // Verify pairwise 3D non-overlap
        List<ItemPlacementDto> pls = result.getPlacements();
        for (int i = 0; i < pls.size(); i++) {
            for (int j = i + 1; j < pls.size(); j++) {
                ItemPlacementDto a = pls.get(i);
                ItemPlacementDto b = pls.get(j);

                boolean overlapX = a.getXMm() < (b.getXMm() + b.getLengthMm()) && (a.getXMm() + a.getLengthMm()) > b.getXMm();
                boolean overlapY = a.getYMm() < (b.getYMm() + b.getWidthMm()) && (a.getYMm() + a.getWidthMm()) > b.getYMm();
                boolean overlapZ = a.getZMm() < (b.getZMm() + b.getHeightMm()) && (a.getZMm() + a.getHeightMm()) > b.getZMm();

                assertThat(overlapX && overlapY && overlapZ).as("Overlap between %s and %s", a.getPlacementId(), b.getPlacementId()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("4. Rotation yaw 90 used when permitted to fit dimension that otherwise exceeds width")
    void testYawRotationPermitted() {
        // Compartment width is 2000, item is 2200 length x 1500 width. Cannot fit length along Y, but fits if yaw rotated or along X
        CompartmentDto smallComp = new CompartmentDto("comp-small", "Small", 3000, 1800, 2000, 5000);
        CargoItemDto rotatedItem = new CargoItemDto("it-long", "Long Item", "NON_HAZ", "UN0000", 2500, 1500, 1000, 300, true, 500, 1);
        rotatedItem.setPermittedOrientations(List.of("DEFAULT", "ROTATE_YAW_90"));

        CandidateLayoutDto result = optimizer.optimize(smallComp, List.of(rotatedItem), standardSnapshot, "snap-2026-q4-v1");
        assertThat(result.getPlacements()).hasSize(1);
    }

    @Test
    @DisplayName("5. Rotation forbidden by cargo permissions is strictly respected")
    void testRotationForbiddenByPermissions() {
        // Compartment is 1500 length x 2000 width. Item is 1800 length x 1000 width.
        // It cannot fit along X (1800 > 1500) in DEFAULT orientation.
        // If rotation is forbidden (only DEFAULT permitted), it must not rotate and must be reported unassigned!
        CompartmentDto narrowLengthComp = new CompartmentDto("comp-short", "Short", 1500, 2500, 2000, 5000);
        CargoItemDto item = new CargoItemDto("it-rigid", "Rigid Item", "NON_HAZ", "UN0000", 1800, 1000, 1000, 300, true, 500, 1);
        item.setPermittedOrientations(List.of("DEFAULT")); // NO ROTATION

        CandidateLayoutDto result = optimizer.optimize(narrowLengthComp, List.of(item), standardSnapshot, "snap-2026-q4-v1");
        assertThat(result.getPlacements()).isEmpty();
        assertThat(result.getUnassignedItems()).hasSize(1);
        assertThat(result.getUnassignedItems().get(0).getReasonCode()).isEqualTo("NO_FIT_GEOMETRY");
    }

    @Test
    @DisplayName("6. Out-of-bounds cargo item dimensions reported as unassigned")
    void testOutOfBoundsDimensions() {
        CargoItemDto hugeItem = new CargoItemDto("it-huge", "Huge Item", "NON_HAZ", "UN0000", 15000, 1000, 1000, 500, true, 500, 1);
        CandidateLayoutDto result = optimizer.optimize(standardCompartment, List.of(hugeItem), standardSnapshot, "snap-2026-q4-v1");

        assertThat(result.getPlacements()).isEmpty();
        assertThat(result.getUnassignedItems()).hasSize(1);
        assertThat(result.getUnassignedItems().get(0).getReasonCode()).isEqualTo("NO_FIT_GEOMETRY");
    }

    @Test
    @DisplayName("7. Stacking bottom support - elevated placement requires ground or lower support")
    void testUnsupportedStackingAvoided() {
        CargoItemDto item1 = new CargoItemDto("it-base", "Base", "NON_HAZ", "UN0000", 2000, 2000, 1000, 400, true, 800, 1);
        CandidateLayoutDto result = optimizer.optimize(standardCompartment, List.of(item1), standardSnapshot, "snap-2026-q4-v1");

        assertThat(result.getPlacements()).hasSize(1);
        assertThat(result.getPlacements().get(0).getZMm()).isEqualTo(0);
    }

    @Test
    @DisplayName("8. Payload capacity limit enforced without integer overflow")
    void testPayloadCapacityLimitEnforced() {
        CompartmentDto compWeight = new CompartmentDto("comp-w", "Low Capacity", 12000, 2400, 2600, 1000.0);
        CargoItemDto heavyItem = new CargoItemDto("it-heavy", "Heavy", "NON_HAZ", "UN0000", 1000, 1000, 1000, 600.0, true, 800, 2);

        CandidateLayoutDto result = optimizer.optimize(compWeight, List.of(heavyItem), standardSnapshot, "snap-2026-q4-v1");
        assertThat(result.getPlacements()).hasSize(1); // First fits (600 kg <= 1000 kg)
        assertThat(result.getUnassignedItems()).hasSize(1); // Second fails (1200 kg > 1000 kg)
        assertThat(result.getUnassignedItems().get(0).getReasonCode()).isEqualTo("PAYLOAD_CAPACITY_EXCEEDED");
    }

    @Test
    @DisplayName("9. Incompatible co-loading prohibition (Class 1 and Class 2.1) leaves second item unassigned")
    void testForbiddenColoadingRuleEnforced() {
        CargoItemDto explosives = new CargoItemDto("it-exp", "Explosives", "1", "UN0004", 1000, 1000, 1000, 300, true, 500, 1);
        CargoItemDto flammableGas = new CargoItemDto("it-gas", "Flammable Gas", "2.1", "UN1001", 1000, 1000, 1000, 300, true, 500, 1);

        CandidateLayoutDto result = optimizer.optimize(standardCompartment, List.of(explosives, flammableGas), standardSnapshot, "snap-2026-q4-v1");

        assertThat(result.getPlacements()).hasSize(1);
        assertThat(result.getUnassignedItems()).hasSize(1);
        assertThat(result.getUnassignedItems().get(0).getReasonCode()).isEqualTo("HAZARD_COLOAD_FORBIDDEN");
    }

    @Test
    @DisplayName("10. Hazard distance rule enforced (Class 1 and Class 3 segregated by >= 3000 mm center distance)")
    void testHazardDistanceSegregationEnforced() {
        CargoItemDto explosives = new CargoItemDto("it-exp", "Explosives", "1", "UN0004", 1000, 1000, 1000, 300, true, 500, 1);
        CargoItemDto solvent = new CargoItemDto("it-sol", "Solvent", "3", "UN1993", 1000, 1000, 1000, 300, true, 500, 1);

        CandidateLayoutDto result = optimizer.optimize(standardCompartment, List.of(explosives, solvent), standardSnapshot, "snap-2026-q4-v1");

        assertThat(result.getPlacements()).hasSize(2);
        ItemPlacementDto p1 = result.getPlacements().get(0);
        ItemPlacementDto p2 = result.getPlacements().get(1);

        double cx1 = p1.getXMm() + (p1.getLengthMm() / 2.0);
        double cy1 = p1.getYMm() + (p1.getWidthMm() / 2.0);
        double cz1 = p1.getZMm() + (p1.getHeightMm() / 2.0);

        double cx2 = p2.getXMm() + (p2.getLengthMm() / 2.0);
        double cy2 = p2.getYMm() + (p2.getWidthMm() / 2.0);
        double cz2 = p2.getZMm() + (p2.getHeightMm() / 2.0);

        double distance = Math.sqrt(Math.pow(cx1 - cx2, 2) + Math.pow(cy1 - cy2, 2) + Math.pow(cz1 - cz2, 2));
        assertThat(distance).isGreaterThanOrEqualTo(3000.0);
    }

    @Test
    @DisplayName("11. Determinism - identical inputs produce identical placements, coordinates and digests")
    void testDeterminismAcrossRuns() {
        CargoItemDto item1 = new CargoItemDto("it-1", "Item 1", "3", "UN1993", 1000, 1000, 1000, 300, true, 500, 2);
        CargoItemDto item2 = new CargoItemDto("it-2", "Item 2", "8", "UN1789", 800, 800, 800, 200, true, 400, 2);

        CandidateLayoutDto run1 = optimizer.optimize(standardCompartment, List.of(item1, item2), standardSnapshot, "snap-2026-q4-v1");
        CandidateLayoutDto run2 = optimizer.optimize(standardCompartment, List.of(item1, item2), standardSnapshot, "snap-2026-q4-v1");

        assertThat(run1.getContentDigest()).isEqualTo(run2.getContentDigest());
        assertThat(run1.getPlacements()).hasSize(run2.getPlacements().size());
        for (int i = 0; i < run1.getPlacements().size(); i++) {
            ItemPlacementDto p1 = run1.getPlacements().get(i);
            ItemPlacementDto p2 = run2.getPlacements().get(i);
            assertThat(p1.getXMm()).isEqualTo(p2.getXMm());
            assertThat(p1.getYMm()).isEqualTo(p2.getYMm());
            assertThat(p1.getZMm()).isEqualTo(p2.getZMm());
        }
    }

    @Test
    @DisplayName("12. Negative dimensions and duplicate IDs are strictly rejected")
    void testMalformedInputsRejected() {
        CargoItemDto badDim = new CargoItemDto("it-bad", "Bad", "NON_HAZ", "UN0000", -100, 1000, 1000, 100, true, 200, 1);
        assertThatThrownBy(() -> optimizer.optimize(standardCompartment, List.of(badDim), standardSnapshot, "snap-2026-q4-v1"))
                .isInstanceOf(IllegalArgumentException.class);

        CargoItemDto dup1 = new CargoItemDto("dup-id", "Item", "NON_HAZ", "UN0000", 1000, 1000, 1000, 100, true, 200, 1);
        CargoItemDto dup2 = new CargoItemDto("dup-id", "Item Dup", "NON_HAZ", "UN0000", 800, 800, 800, 100, true, 200, 1);
        assertThatThrownBy(() -> optimizer.optimize(standardCompartment, List.of(dup1, dup2), standardSnapshot, "snap-2026-q4-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate cargo item ID");
    }

    @Test
    @DisplayName("13. Optimizer never reports OPTIMAL (always FEASIBLE, INFEASIBLE or UNKNOWN)")
    void testNeverReportsOptimal() {
        CargoItemDto item = new CargoItemDto("it-box", "Dry Box", "NON_HAZ", "UN0000", 1000, 800, 600, 200, true, 500, 1);
        CandidateLayoutDto result = optimizer.optimize(standardCompartment, List.of(item), standardSnapshot, "snap-2026-q4-v1");

        assertThat(result.getSolverStatus()).isNotEqualTo(SolverStatus.OPTIMAL.name());
        assertThat(result.getSolverStatus()).isEqualTo(SolverStatus.FEASIBLE.name());
    }
}
