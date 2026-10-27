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

    @Test
    void shouldRejectApprovalWhenDigestOrVersionMismatches() {
        CompartmentDto comp = new CompartmentDto("comp-2", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-002", 1, "snap-002", comp);

        ItemPlacementDto item = new ItemPlacementDto("pl-1", "item-1", 1, "3", "UN1203", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT", false, 0.0);
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-002", 1, "comp-2", null, "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item)
        );
        AssessmentEntity candidateReady = workflowService.recordCandidateLayout(assessment.getId(), candidate);
        String realDigest = candidateReady.getCandidateContentDigest();
        assertThat(realDigest).isNotBlank();

        // Mark validation passed
        ValidationResultDto valPassed = new ValidationResultDto("val-002", true, "1.0.0", 1, Collections.emptyList());
        workflowService.recordValidationOutcome(assessment.getId(), valPassed);

        // 1. Rejects mismatched expectedDigest (caller tampering / forged digest)
        assertThatThrownBy(() -> workflowService.approveCandidate(
                assessment.getId(), 1, "tampered-digest-000000000000000000000000000000000000000000000000", "planner-1", "tampered approval"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Candidate digest mismatch");

        // 2. Rejects mismatched layout version (stale approval)
        assertThatThrownBy(() -> workflowService.approveCandidate(
                assessment.getId(), 2, realDigest, "planner-1", "stale version approval"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Candidate layout version mismatch");

        // 3. Accepts when version and digest match exactly
        AssessmentEntity approved = workflowService.approveCandidate(
                assessment.getId(), 1, realDigest, "planner-1", "approved layout"
        );
        assertThat(approved.getWorkflowStatus()).isEqualTo("APPROVED");
        assertThat(approved.getApprovalDecision()).isEqualTo("APPROVED");
    }

    @Test
    void shouldRejectApprovalWhenValidationFailedOrMissing() {
        CompartmentDto comp = new CompartmentDto("comp-3", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-003", 1, "snap-003", comp);

        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-003", 1, "comp-3", null, "FEASIBLE", "HEURISTIC_EP_3D_V1", Collections.emptyList()
        );
        AssessmentEntity candidateReady = workflowService.recordCandidateLayout(assessment.getId(), candidate);
        String digest = candidateReady.getCandidateContentDigest();

        // Before any validation: cannot approve
        assertThatThrownBy(() -> workflowService.approveCandidate(
                assessment.getId(), 1, digest, "planner-1", "premature"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Assessment cannot be approved in current state: CANDIDATE_READY");

        // Record failed validation
        ValidationResultDto valFailed = new ValidationResultDto("val-failed", false, "1.0.0", 1, List.of(
                new ValidationFindingDto("CRITICAL", "HAZARD_SEGREGATION_VIOLATION", "Proximity violation", List.of("item-1", "item-2"))
        ));
        AssessmentEntity failedAssessment = workflowService.recordValidationOutcome(assessment.getId(), valFailed);
        assertThat(failedAssessment.getWorkflowStatus()).isEqualTo("VALIDATION_FAILED");

        // Attempt approve on failed validation
        assertThatThrownBy(() -> workflowService.approveCandidate(
                assessment.getId(), 1, digest, "planner-1", "approve failed"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Assessment cannot be approved in current state: VALIDATION_FAILED");
    }
}
