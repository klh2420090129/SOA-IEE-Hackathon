package org.soa8.freight.declaration;

import org.junit.jupiter.api.Test;
import org.soa8.freight.common.constants.EventTopics;
import org.soa8.freight.common.events.CargoLoadRejectedPayload;
import org.soa8.freight.common.events.DomainEvent;
import org.soa8.freight.declaration.model.CargoDeclaration;
import org.soa8.freight.declaration.model.DeclarationWorkflowProjection;
import org.soa8.freight.declaration.repository.CargoDeclarationRepository;
import org.soa8.freight.declaration.repository.DeclarationWorkflowProjectionRepository;
import org.soa8.freight.declaration.service.DeclarationWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false"
})
class CargoDeclarationWorkflowTest {

    @Autowired
    private DeclarationWorkflowService workflowService;

    @Autowired
    private CargoDeclarationRepository declarationRepository;

    @Autowired
    private DeclarationWorkflowProjectionRepository projectionRepository;

    @Test
    void shouldProcessCargoLoadRejectedAndBeIdempotentOnDuplicate() {
        String declarationId = "decl-test-101";
        CargoDeclaration decl = new CargoDeclaration(declarationId, "REF-101", "shipper-1", "A", "B");
        declarationRepository.save(decl);

        CargoLoadRejectedPayload payload = new CargoLoadRejectedPayload(
                "asmt-test-202", declarationId, 1, 1, "digest-abc",
                "rules-snap-1", "planner-1", "HAZARD_SEGREGATION_VIOLATION",
                "Explosives cannot be adjacent to flammables"
        );

        DomainEvent<CargoLoadRejectedPayload> event = new DomainEvent<>(
                EventTopics.EVENT_TYPE_CARGO_LOAD_REJECTED,
                "asmt-test-202",
                "Assessment",
                EventTopics.SCHEMA_VERSION_1,
                "corr-12345",
                payload
        );

        // First delivery: should succeed and create projection
        boolean firstProcessed = workflowService.processCargoLoadRejected(event);
        assertThat(firstProcessed).isTrue();

        DeclarationWorkflowProjection proj = projectionRepository.findById(declarationId).orElseThrow();
        assertThat(proj.getWorkflowStatus()).isEqualTo("REJECTED");
        assertThat(proj.getRejectionReasonCode()).isEqualTo("HAZARD_SEGREGATION_VIOLATION");

        // Second delivery (duplicate replay): should be safely ignored
        boolean secondProcessed = workflowService.processCargoLoadRejected(event);
        assertThat(secondProcessed).isFalse();
    }
}
