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
}
