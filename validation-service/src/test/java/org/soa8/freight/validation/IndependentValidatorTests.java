package org.soa8.freight.validation;

import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.*;
import org.soa8.freight.validation.engine.IndependentValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "eureka.client.enabled=false"
})
class IndependentValidatorTests {

    @Autowired
    private IndependentValidator validator;

    @Test
    void shouldPassValidNonOverlappingLayout() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        ItemPlacementDto item1 = new ItemPlacementDto("p1", "it1", 0, "3", "UN1993", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT");
        ItemPlacementDto item2 = new ItemPlacementDto("p2", "it2", 0, "8", "UN1789", 1500, 0, 0, 1000, 1000, 1000, 500, "DEFAULT");

        CandidateLayoutDto layout = new CandidateLayoutDto("l1", 1, "comp-1", "digest", "FEASIBLE", "CONTROLLED_STUB", List.of(item1, item2));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isTrue();
        assertThat(res.getFindings()).isEmpty();
    }

    @Test
    void shouldDetect3DOverlap() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        // Overlapping items in (0..1000)
        ItemPlacementDto item1 = new ItemPlacementDto("p1", "it1", 0, "3", "UN1993", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT");
        ItemPlacementDto item2 = new ItemPlacementDto("p2", "it2", 0, "8", "UN1789", 500, 500, 500, 1000, 1000, 1000, 500, "DEFAULT");

        CandidateLayoutDto layout = new CandidateLayoutDto("l1", 1, "comp-1", "digest", "FEASIBLE", "CONTROLLED_STUB", List.of(item1, item2));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isFalse();
        assertThat(res.getFindings()).anyMatch(f -> "OVERLAP_DETECTED".equals(f.getCode()));
    }

    @Test
    void shouldDetectHazardSegregationDistanceViolation() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        // Distance is ~1200mm, but rule demands 3000mm between Class 1 and Class 3
        ItemPlacementDto item1 = new ItemPlacementDto("p1", "it1", 0, "1", "UN0004", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT");
        ItemPlacementDto item2 = new ItemPlacementDto("p2", "it2", 0, "3", "UN1993", 1200, 0, 0, 1000, 1000, 1000, 500, "DEFAULT");

        CandidateLayoutDto layout = new CandidateLayoutDto("l1", 1, "comp-1", "digest", "FEASIBLE", "CONTROLLED_STUB", List.of(item1, item2));

        CompatibilityRuleDto rule = new CompatibilityRuleDto("r1", "CMV-HAZ-1-3", "1", "3", "SEPARATED_BY_DISTANCE", 3000, "Must segregate 3000mm", "INDIA");
        RuleSnapshotDto snapshot = new RuleSnapshotDto("snap-1", "INDIA", null, "PUBLISHED", "digest", List.of(rule));

        ValidationResultDto res = validator.validate(layout, comp, snapshot);
        assertThat(res.isPassed()).isFalse();
        assertThat(res.getFindings()).anyMatch(f -> "HAZARD_SEGREGATION_VIOLATION".equals(f.getCode()));
    }

    @Test
    void shouldPassFloorSupportedAndProperlyStackedCargo() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        // Base item resting on floor (z=0)
        ItemPlacementDto base = new ItemPlacementDto("p1", "it-base", 0, "NON_HAZ", "UN0000",
                0, 0, 0, 1000, 1000, 1000, 400, "DEFAULT", true, 800.0);
        // Upper item resting directly on base (z=1000), 100% supported
        ItemPlacementDto upper = new ItemPlacementDto("p2", "it-upper", 0, "NON_HAZ", "UN0000",
                0, 0, 1000, 1000, 1000, 1000, 300, "DEFAULT", true, 500.0);

        CandidateLayoutDto layout = new CandidateLayoutDto("l-stack", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(base, upper));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isTrue();
        assertThat(res.getFindings()).isEmpty();
    }

    @Test
    void shouldRejectUnsupportedCargoFloatingInAir() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        // Floating item with Z > 0 and no base underneath
        ItemPlacementDto floating = new ItemPlacementDto("p1", "it-float", 0, "NON_HAZ", "UN0000",
                0, 0, 500, 1000, 1000, 1000, 200, "DEFAULT", true, 500.0);

        CandidateLayoutDto layout = new CandidateLayoutDto("l-float", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(floating));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isFalse();
        assertThat(res.getFindings()).anyMatch(f -> "UNSUPPORTED_CARGO".equals(f.getCode()));
    }

    @Test
    void shouldRejectExcessiveOverhangWithInsufficientSupport() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        // Base item is 1000 x 1000
        ItemPlacementDto base = new ItemPlacementDto("p1", "it-base", 0, "NON_HAZ", "UN0000",
                0, 0, 0, 1000, 1000, 1000, 400, "DEFAULT", true, 800.0);
        // Upper item is shifted by 800mm along X (only 20% supported: 200 x 1000 / 1000000 = 0.2 < 0.5)
        ItemPlacementDto overhanging = new ItemPlacementDto("p2", "it-overhang", 0, "NON_HAZ", "UN0000",
                800, 0, 1000, 1000, 1000, 1000, 300, "DEFAULT", true, 500.0);

        CandidateLayoutDto layout = new CandidateLayoutDto("l-overhang", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(base, overhanging));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isFalse();
        assertThat(res.getFindings()).anyMatch(f -> "INSUFFICIENT_SUPPORT".equals(f.getCode()));
    }

    @Test
    void shouldRejectCargoStackedOnNonStackableBase() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        // Base item explicitly NOT stackable
        ItemPlacementDto nonStackableBase = new ItemPlacementDto("p1", "it-fragile", 0, "NON_HAZ", "UN0000",
                0, 0, 0, 1000, 1000, 1000, 400, "DEFAULT", false, 0.0);
        ItemPlacementDto upper = new ItemPlacementDto("p2", "it-upper", 0, "NON_HAZ", "UN0000",
                0, 0, 1000, 1000, 1000, 1000, 300, "DEFAULT", true, 500.0);

        CandidateLayoutDto layout = new CandidateLayoutDto("l-fragile", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(nonStackableBase, upper));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isFalse();
        assertThat(res.getFindings()).anyMatch(f -> "NON_STACKABLE_LOAD_BEARING".equals(f.getCode()));
    }

    @Test
    void shouldRejectWhenStackWeightExceedsMaxLimit() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        // Base item allows max 400 kg stacked weight
        ItemPlacementDto base = new ItemPlacementDto("p1", "it-base", 0, "NON_HAZ", "UN0000",
                0, 0, 0, 1000, 1000, 1000, 400, "DEFAULT", true, 400.0);
        // Upper item weighs 500 kg (> 400 kg limit)
        ItemPlacementDto heavyUpper = new ItemPlacementDto("p2", "it-heavy", 0, "NON_HAZ", "UN0000",
                0, 0, 1000, 1000, 1000, 1000, 500, "DEFAULT", true, 800.0);

        CandidateLayoutDto layout = new CandidateLayoutDto("l-overweight", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(base, heavyUpper));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isFalse();
        assertThat(res.getFindings()).anyMatch(f -> "STACK_WEIGHT_LIMIT_EXCEEDED".equals(f.getCode()));
    }

    @Test
    void shouldRejectCargoStackedOnUnknownStackableBaseWithUnverifiedStackCapability() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        // Base item has stackable = null (unknown capability)
        ItemPlacementDto unknownBase = new ItemPlacementDto("p1", "it-unknown", 0, "NON_HAZ", "UN0000",
                0, 0, 0, 1000, 1000, 1000, 400, "DEFAULT", null, null);
        ItemPlacementDto upper = new ItemPlacementDto("p2", "it-upper", 0, "NON_HAZ", "UN0000",
                0, 0, 1000, 1000, 1000, 1000, 300, "DEFAULT", true, 500.0);

        CandidateLayoutDto layout = new CandidateLayoutDto("l-unknown-stack", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(unknownBase, upper));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isFalse();
        assertThat(res.getFindings()).anyMatch(f -> "UNVERIFIED_STACK_CAPABILITY".equals(f.getCode()));
    }

    @Test
    void shouldPassFloorOnlyCargoWithUnknownStackableMetadataWhenCarryingNoUpperLoad() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 12000, 2400, 2600, 25000);

        // Floor items placed directly on floor (z=0) with unknown stackable capability (stackable = null)
        ItemPlacementDto floorItem1 = new ItemPlacementDto("p1", "it1", 0, "NON_HAZ", "UN0000",
                0, 0, 0, 1000, 1000, 1000, 400, "DEFAULT", null, null);
        ItemPlacementDto floorItem2 = new ItemPlacementDto("p2", "it2", 0, "NON_HAZ", "UN0000",
                1200, 0, 0, 1000, 1000, 1000, 400, "DEFAULT", null, null);

        CandidateLayoutDto layout = new CandidateLayoutDto("l-floor-unknown", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(floorItem1, floorItem2));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isTrue();
        assertThat(res.getFindings()).isEmpty();
    }

    @Test
    void shouldCalculateExactCoGForKnownHandCalculatedLayout() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 10000, 2000, 2000, 25000);

        // Package 1: mass=100kg, dims=(1000, 1000, 1000), pos=(0, 0, 0)
        // Center: (500, 500, 500)
        ItemPlacementDto item1 = new ItemPlacementDto("p1", "it1", 0, "NON_HAZ", "UN0000",
                0, 0, 0, 1000, 1000, 1000, 100.0, "DEFAULT");

        // Package 2: mass=300kg, dims=(1000, 1000, 1000), pos=(2000, 1000, 0)
        // Center: (2500, 1500, 500)
        ItemPlacementDto item2 = new ItemPlacementDto("p2", "it2", 0, "NON_HAZ", "UN0000",
                2000, 1000, 0, 1000, 1000, 1000, 300.0, "DEFAULT");

        // Total mass = 400kg
        // CoG_x = (100*500 + 300*2500) / 400 = (50,000 + 750,000) / 400 = 800,000 / 400 = 2000.0 mm
        // CoG_y = (100*500 + 300*1500) / 400 = (50,000 + 450,000) / 400 = 500,000 / 400 = 1250.0 mm
        // CoG_z = (100*500 + 300*500) / 400 = 500.0 mm
        CandidateLayoutDto layout = new CandidateLayoutDto("l-cog", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item1, item2));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isTrue();
        assertThat(res.getTotalMassKg()).isEqualTo(400.0);
        assertThat(res.getCogXMm()).isEqualTo(2000.0);
        assertThat(res.getCogYMm()).isEqualTo(1250.0);
        assertThat(res.getCogZMm()).isEqualTo(500.0);
        assertThat(res.getLoadBalanceStatus()).isEqualTo("NOT_CONFIGURED"); // Limits unconfigured
    }

    @Test
    void shouldCalculateCorrectCoGWithRotatedPackageAndUnequalMass() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 10000, 2400, 2600, 25000);

        // Package 1: rotated yaw 90, so placed length=800, width=1200, height=500.
        // pos=(1000, 500, 0), mass=200kg
        // Center: cx = 1000 + 400 = 1400, cy = 500 + 600 = 1100, cz = 250
        ItemPlacementDto item1 = new ItemPlacementDto("p1", "it1", 0, "NON_HAZ", "UN0000",
                1000, 500, 0, 800, 1200, 500, 200.0, "ROTATE_YAW_90");

        // Package 2: default orientation, pos=(4000, 200, 0), mass=600kg, dims=(1000, 800, 1000)
        // Center: cx = 4000 + 500 = 4500, cy = 200 + 400 = 600, cz = 500
        ItemPlacementDto item2 = new ItemPlacementDto("p2", "it2", 0, "NON_HAZ", "UN0000",
                4000, 200, 0, 1000, 800, 1000, 600.0, "DEFAULT");

        // Total mass = 800kg
        // CoG_x = (200*1400 + 600*4500) / 800 = (280,000 + 2,700,000) / 800 = 2,980,000 / 800 = 3725.0 mm
        // CoG_y = (200*1100 + 600*600) / 800 = (220,000 + 360,000) / 800 = 580,000 / 800 = 725.0 mm
        // CoG_z = (200*250 + 600*500) / 800 = (50,000 + 300,000) / 800 = 350,000 / 800 = 437.5 mm
        CandidateLayoutDto layout = new CandidateLayoutDto("l-rotated", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item1, item2));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isTrue();
        assertThat(res.getTotalMassKg()).isEqualTo(800.0);
        assertThat(res.getCogXMm()).isEqualTo(3725.0);
        assertThat(res.getCogYMm()).isEqualTo(725.0);
        assertThat(res.getCogZMm()).isEqualTo(437.5);
    }

    @Test
    void shouldHandleEmptyLayoutSafelyWithoutEmittingNaN() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 10000, 2400, 2600, 25000);
        CandidateLayoutDto layout = new CandidateLayoutDto("l-empty", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of());

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isTrue();
        assertThat(res.getTotalMassKg()).isEqualTo(0.0);
        assertThat(res.getCogXMm()).isEqualTo(5000.0); // Defaults to midpoint of length
        assertThat(res.getCogYMm()).isEqualTo(1200.0); // Defaults to midpoint of width
        assertThat(res.getCogZMm()).isEqualTo(0.0);
        assertThat(Double.isNaN(res.getCogXMm())).isFalse();
        assertThat(Double.isInfinite(res.getCogXMm())).isFalse();
        assertThat(res.getLoadBalanceStatus()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void shouldEnforceConfiguredCoGBoundsCorrectly() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 10000, 2000, 2000, 25000);
        // Authoritative CoG longitudinal envelope: [3000mm, 6000mm]
        comp.setMinCogXMm(3000.0);
        comp.setMaxCogXMm(6000.0);

        // Item placed near front: Center cx = 1000mm (< 3000mm)
        ItemPlacementDto item = new ItemPlacementDto("p1", "it1", 0, "NON_HAZ", "UN0000",
                500, 500, 0, 1000, 1000, 1000, 500.0, "DEFAULT");
        CandidateLayoutDto layout = new CandidateLayoutDto("l-out-of-bounds", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isFalse();
        assertThat(res.getLoadBalanceStatus()).isEqualTo("OUT_OF_BOUNDS");
        assertThat(res.getFindings()).anyMatch(f -> "COG_OUT_OF_BOUNDS".equals(f.getCode()));

        // Exactly on configured boundary: Center cx = 3000mm (placement x = 2500, L = 1000 -> cx = 3000.0)
        ItemPlacementDto boundaryItem = new ItemPlacementDto("p2", "it2", 0, "NON_HAZ", "UN0000",
                2500, 500, 0, 1000, 1000, 1000, 500.0, "DEFAULT");
        CandidateLayoutDto boundaryLayout = new CandidateLayoutDto("l-boundary", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(boundaryItem));

        ValidationResultDto boundaryRes = validator.validate(boundaryLayout, comp, null);
        assertThat(boundaryRes.isPassed()).isTrue();
        assertThat(boundaryRes.getCogXMm()).isEqualTo(3000.0);
        assertThat(boundaryRes.getLoadBalanceStatus()).isEqualTo("BALANCED");
    }

    @Test
    void shouldRejectInvalidMassOrNonFiniteGeometry() {
        CompartmentDto comp = new CompartmentDto("comp-1", "40ft", 10000, 2000, 2000, 25000);

        // Negative mass
        ItemPlacementDto badMassItem = new ItemPlacementDto("p1", "it1", 0, "NON_HAZ", "UN0000",
                0, 0, 0, 1000, 1000, 1000, -50.0, "DEFAULT");
        CandidateLayoutDto layout = new CandidateLayoutDto("l-bad-mass", 1, "comp-1", "digest", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(badMassItem));

        ValidationResultDto res = validator.validate(layout, comp, null);
        assertThat(res.isPassed()).isFalse();
        assertThat(res.getLoadBalanceStatus()).isEqualTo("INVALID_DATA");
        assertThat(res.getFindings()).anyMatch(f -> "INVALID_MASS_DATA".equals(f.getCode()));
    }
}
