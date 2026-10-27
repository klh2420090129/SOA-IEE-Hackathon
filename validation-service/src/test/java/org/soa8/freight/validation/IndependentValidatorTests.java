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
}
