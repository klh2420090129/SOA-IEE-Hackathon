package org.soa8.freight.compartment;

import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.*;
import org.soa8.freight.compartment.model.AssessmentEntity;
import org.soa8.freight.compartment.model.OutboxEventEntity;
import org.soa8.freight.compartment.repository.OutboxEventRepository;
import org.soa8.freight.compartment.service.AssessmentWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false"
})
class CompartmentAssessmentWorkflowTest {

    @Autowired
    private AssessmentWorkflowService workflowService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldFollowAssessmentStateTransitionsAndPublishOutboxOnRejection() {
        CompartmentDto comp = new CompartmentDto("comp-1", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-001", 1, "snap-001", comp);
        assertThat(assessment.getWorkflowStatus()).isEqualTo("DRAFT");

        // Record Candidate
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-001", 1, "comp-1", null, "FEASIBLE", "CONTROLLED_STUB", Collections.emptyList()
        );
        AssessmentEntity candidateReadyAssessment = workflowService.recordCandidateLayout(assessment.getId(), candidate);
        assertThat(candidateReadyAssessment.getWorkflowStatus()).isEqualTo("CANDIDATE_READY");
        assertThat(candidateReadyAssessment.getCandidateContentDigest()).isNotBlank();

        String assessmentId = candidateReadyAssessment.getId();
        String digest = candidateReadyAssessment.getCandidateContentDigest();

        // Attempt approve before validation -> should fail
        assertThatThrownBy(() -> workflowService.approveCandidate(
                assessmentId, 1, digest, "planner-1", "premature approval"
        )).isInstanceOf(IllegalStateException.class);

        // Record Validation Outcome: Passed
        ValidationResultDto valPassed = new ValidationResultDto("val-001", true, "1.0.0", 2, Collections.emptyList());
        AssessmentEntity validatedAssessment = workflowService.recordValidationOutcome(assessmentId, valPassed);
        assertThat(validatedAssessment.getWorkflowStatus()).isEqualTo("PENDING_APPROVAL");

        // Planner Rejection
        AssessmentEntity rejectedAssessment = workflowService.rejectCandidate(
                assessmentId, 1, "planner-1", "HAZARD_SEGREGATION_VIOLATION", "Dangerous proximity"
        );
        assertThat(rejectedAssessment.getWorkflowStatus()).isEqualTo("REJECTED");

        // Verify outbox event written
        List<OutboxEventEntity> outbox = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        assertThat(outbox).isNotEmpty();
        OutboxEventEntity event = outbox.getFirst();
        assertThat(event.getEventType()).isEqualTo("CargoLoadRejected");
        assertThat(event.getPayloadJson()).contains("HAZARD_SEGREGATION_VIOLATION");
    }
}
