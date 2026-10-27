package org.soa8.freight.optimization.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.*;
import org.soa8.freight.optimization.engine.Deterministic3DHeuristicOptimizer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicBenchmarkTest {

    private Deterministic3DHeuristicOptimizer optimizer;
    private CompartmentDto standardTrailer;
    private RuleSnapshotDto standardSnapshot;

    @BeforeEach
    void setUp() {
        optimizer = new Deterministic3DHeuristicOptimizer();
        standardTrailer = new CompartmentDto("comp-trailer-bench", "Trailer 40ft", 12000, 2400, 2600, 25000);

        CompatibilityRuleDto ruleDist = new CompatibilityRuleDto(
                "rule-cmv-001", "CMV-HAZ-1-3", "1", "3", "SEPARATED_BY_DISTANCE", 3000, "Min 3000mm separation", "INDIA"
        );
        CompatibilityRuleDto ruleForbid = new CompatibilityRuleDto(
                "rule-cmv-002", "CMV-HAZ-1-2.1", "1", "2.1", "FORBIDDEN", 0, "Forbidden co-loading", "INDIA"
        );
        standardSnapshot = new RuleSnapshotDto("snap-2026-q4-v1", "INDIA", null, "PUBLISHED", "digest-bench", List.of(ruleDist, ruleForbid));
    }

    @Test
    @DisplayName("Benchmark 5 handling units")
    void benchmark5Units() {
        List<CargoItemDto> items = List.of(
                new CargoItemDto("it-drum-class3", "Solvent Drums", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 3),
                new CargoItemDto("it-box-class8", "Acid Boxes", "8", "UN1789", 1200, 800, 800, 300, true, 600, 2)
        );

        long start = System.currentTimeMillis();
        CandidateLayoutDto candidate = optimizer.optimize(standardTrailer, items, standardSnapshot, "snap-2026-q4-v1");
        long duration = System.currentTimeMillis() - start;

        assertThat(candidate.getPlacements()).hasSize(5);
        assertThat(candidate.getUnassignedItems()).isEmpty();
        assertThat(duration).isLessThan(2000);
        System.out.printf("[BENCHMARK-5] Placed: %d, Unassigned: %d, Duration: %d ms, Digest: %s%n",
                candidate.getPlacements().size(), candidate.getUnassignedItems().size(), duration, candidate.getContentDigest());
    }

    @Test
    @DisplayName("Benchmark 10 handling units with segregation constraints")
    void benchmark10Units() {
        List<CargoItemDto> items = List.of(
                new CargoItemDto("it-haz1-exp", "Explosives C1", "1", "UN0004", 800, 800, 800, 150, true, 400, 2),
                new CargoItemDto("it-haz3-sol", "Solvents C3", "3", "UN1993", 1000, 1000, 1000, 350, true, 700, 4),
                new CargoItemDto("it-nonhaz-box", "Standard Dry", "NON_HAZ", "UN0000", 1200, 800, 800, 250, true, 500, 4)
        );

        long start = System.currentTimeMillis();
        CandidateLayoutDto candidate = optimizer.optimize(standardTrailer, items, standardSnapshot, "snap-2026-q4-v1");
        long duration = System.currentTimeMillis() - start;

        assertThat(candidate.getPlacements()).hasSize(10);
        assertThat(candidate.getUnassignedItems()).isEmpty();
        assertThat(duration).isLessThan(3000);
        System.out.printf("[BENCHMARK-10] Placed: %d, Unassigned: %d, Duration: %d ms, Digest: %s%n",
                candidate.getPlacements().size(), candidate.getUnassignedItems().size(), duration, candidate.getContentDigest());
    }

    @Test
    @DisplayName("Benchmark 20 handling units across mixed hazard classes")
    void benchmark20Units() {
        List<CargoItemDto> items = List.of(
                new CargoItemDto("it-c1", "Explosive C1", "1", "UN0004", 600, 600, 600, 100, true, 300, 3),
                new CargoItemDto("it-c3", "Solvent C3", "3", "UN1993", 800, 800, 800, 250, true, 600, 5),
                new CargoItemDto("it-c8", "Corrosive C8", "8", "UN1789", 800, 800, 800, 200, true, 500, 6),
                new CargoItemDto("it-nh", "General Cargo", "NON_HAZ", "UN0000", 1000, 800, 600, 180, true, 400, 6)
        );

        long start = System.currentTimeMillis();
        CandidateLayoutDto candidate = optimizer.optimize(standardTrailer, items, standardSnapshot, "snap-2026-q4-v1");
        long duration = System.currentTimeMillis() - start;

        assertThat(candidate.getPlacements().size() + candidate.getUnassignedItems().size()).isEqualTo(20);
        assertThat(duration).isLessThan(5000);
        System.out.printf("[BENCHMARK-20] Placed: %d, Unassigned: %d, Duration: %d ms, Digest: %s%n",
                candidate.getPlacements().size(), candidate.getUnassignedItems().size(), duration, candidate.getContentDigest());
    }
}
