package org.soa8.freight.optimization;

import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.CandidateLayoutDto;
import org.soa8.freight.common.model.CargoItemDto;
import org.soa8.freight.optimization.stub.ControlledOptimizerStub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "eureka.client.enabled=false"
})
class OptimizationControlledStubTest {

    @Autowired
    private ControlledOptimizerStub stub;

    @Test
    void shouldProduceControlledStubOutputWithCorrectProvenance() {
        CargoItemDto item1 = new CargoItemDto("item-1", "Drums", "3", "UN1993", 1000, 800, 1000, 300, true, 600, 2);
        CandidateLayoutDto candidate = stub.generateControlledStubLayout("comp-1", 12000, 2400, 2600, List.of(item1));

        assertThat(candidate.getProvenance()).isEqualTo("CONTROLLED_STUB");
        assertThat(candidate.getSolverStatus()).isEqualTo("FEASIBLE");
        assertThat(candidate.getContentDigest()).isNotBlank();
        assertThat(candidate.getPlacements()).hasSize(2);
    }
}
