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

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.soa8.freight.compartment.client.ValidationServiceClient validationClient;

    @Test
    void shouldFollowAssessmentStateTransitionsAndPublishOutboxOnRejection() {
        CompartmentDto comp = new CompartmentDto("comp-1", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-001", 1, "snap-001", comp);
        assertThat(assessment.getWorkflowStatus()).isEqualTo("DRAFT");

        // Record Candidate with valid placements
        ItemPlacementDto item = new ItemPlacementDto("pl-001", "item-001", 1, "3", "UN1203", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT", false, 0.0);
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-001", 1, "comp-1", null, "FEASIBLE", "CONTROLLED_STUB", List.of(item)
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

        // Record Validation Outcome: Passed (simulated authoritative record)
        ValidationResultDto valPassed = new ValidationResultDto("val-001", true, "1.0.0", 2, Collections.emptyList());
        valPassed.setCandidateLayoutId("layout-001");
        valPassed.setCandidateLayoutVersion(1);
        valPassed.setCandidateContentDigest(digest);
        valPassed.setRuleSnapshotId("snap-001");

        // When validationClient queries validation-service, return authoritative record
        org.mockito.Mockito.when(validationClient.getValidationRun("val-001")).thenReturn(java.util.Optional.of(valPassed));

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

        // Mark validation passed authoritatively
        ValidationResultDto valPassed = new ValidationResultDto("val-002", true, "1.0.0", 1, Collections.emptyList());
        valPassed.setCandidateLayoutId("layout-002");
        valPassed.setCandidateLayoutVersion(1);
        valPassed.setCandidateContentDigest(realDigest);
        valPassed.setRuleSnapshotId("snap-002");
        org.mockito.Mockito.when(validationClient.getValidationRun("val-002")).thenReturn(java.util.Optional.of(valPassed));

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
    void shouldRejectFabricatedValidationId() {
        CompartmentDto comp = new CompartmentDto("comp-fab", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-fab", 1, "snap-fab", comp);

        ItemPlacementDto item = new ItemPlacementDto("pl-fab", "item-fab", 1, "3", "UN1203", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT", false, 0.0);
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-fab", 1, "comp-fab", null, "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item)
        );
        workflowService.recordCandidateLayout(assessment.getId(), candidate);

        // Fabricated validation ID not found in validation-service
        org.mockito.Mockito.when(validationClient.getValidationRun("val-fake-999")).thenReturn(java.util.Optional.empty());

        ValidationResultDto fabricated = new ValidationResultDto("val-fake-999", true, "1.0.0", 1, Collections.emptyList());
        assertThatThrownBy(() -> workflowService.recordValidationOutcome(assessment.getId(), fabricated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fabricated or non-existent validation ID");
    }

    @Test
    void shouldRejectCallerForgedPassedWhenAuthoritativeRecordFailed() {
        CompartmentDto comp = new CompartmentDto("comp-forged", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-forged", 1, "snap-forged", comp);

        ItemPlacementDto item = new ItemPlacementDto("pl-f", "item-f", 1, "3", "UN1203", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT", false, 0.0);
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-f", 1, "comp-forged", null, "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item)
        );
        AssessmentEntity ready = workflowService.recordCandidateLayout(assessment.getId(), candidate);

        // Authoritative record in validation service actually FAILED with findings
        ValidationResultDto authoritativelyFailed = new ValidationResultDto("val-failed-real", false, "1.0.0", 1, List.of(
                new ValidationFindingDto("CRITICAL", "HAZARD_SEGREGATION_VIOLATION", "Violation", List.of("item-f"))
        ));
        authoritativelyFailed.setCandidateLayoutId("layout-f");
        authoritativelyFailed.setCandidateLayoutVersion(1);
        authoritativelyFailed.setCandidateContentDigest(ready.getCandidateContentDigest());
        authoritativelyFailed.setRuleSnapshotId("snap-forged");
        org.mockito.Mockito.when(validationClient.getValidationRun("val-failed-real")).thenReturn(java.util.Optional.of(authoritativelyFailed));

        // Caller attempts to submit passed=true for that validation ID
        ValidationResultDto callerForgedPassed = new ValidationResultDto("val-failed-real", true, "1.0.0", 1, Collections.emptyList());
        AssessmentEntity result = workflowService.recordValidationOutcome(assessment.getId(), callerForgedPassed);

        // System must ignore caller's passed=true and record authoritative VALIDATION_FAILED
        assertThat(result.getWorkflowStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.isValidationPassed()).isFalse();

        // Planner cannot approve
        assertThatThrownBy(() -> workflowService.approveCandidate(
                assessment.getId(), 1, ready.getCandidateContentDigest(), "planner-1", "approve"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("VALIDATION_FAILED");
    }

    @Test
    void shouldRejectValidationWithMismatchedCandidateDigest() {
        CompartmentDto comp = new CompartmentDto("comp-dm", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-dm", 1, "snap-dm", comp);

        ItemPlacementDto item = new ItemPlacementDto("pl-dm", "item-dm", 1, "3", "UN1203", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT", false, 0.0);
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-dm", 1, "comp-dm", null, "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item)
        );
        workflowService.recordCandidateLayout(assessment.getId(), candidate);

        // Authoritative record validated a different candidate digest
        ValidationResultDto valDiffDigest = new ValidationResultDto("val-diff-digest", true, "1.0.0", 1, Collections.emptyList());
        valDiffDigest.setCandidateLayoutId("layout-dm");
        valDiffDigest.setCandidateLayoutVersion(1);
        valDiffDigest.setCandidateContentDigest("other-digest-00000000000000000000000000000000000000000000");
        valDiffDigest.setRuleSnapshotId("snap-dm");
        org.mockito.Mockito.when(validationClient.getValidationRun("val-diff-digest")).thenReturn(java.util.Optional.of(valDiffDigest));

        assertThatThrownBy(() -> workflowService.recordValidationOutcome(assessment.getId(), valDiffDigest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Candidate content digest mismatch");
    }

    @Test
    void shouldRejectValidationWithMismatchedRuleSnapshot() {
        CompartmentDto comp = new CompartmentDto("comp-sm", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-sm", 1, "snap-2026-q4-v1", comp);

        ItemPlacementDto item = new ItemPlacementDto("pl-sm", "item-sm", 1, "3", "UN1203", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT", false, 0.0);
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-sm", 1, "comp-sm", null, "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item)
        );
        AssessmentEntity ready = workflowService.recordCandidateLayout(assessment.getId(), candidate);

        // Authoritative record validated a different snapshot ID (e.g., outdated or different jurisdiction)
        ValidationResultDto valDiffSnapshot = new ValidationResultDto("val-diff-snap", true, "1.0.0", 1, Collections.emptyList());
        valDiffSnapshot.setCandidateLayoutId("layout-sm");
        valDiffSnapshot.setCandidateLayoutVersion(1);
        valDiffSnapshot.setCandidateContentDigest(ready.getCandidateContentDigest());
        valDiffSnapshot.setRuleSnapshotId("snap-outdated-2025");
        org.mockito.Mockito.when(validationClient.getValidationRun("val-diff-snap")).thenReturn(java.util.Optional.of(valDiffSnapshot));

        assertThatThrownBy(() -> workflowService.recordValidationOutcome(assessment.getId(), valDiffSnapshot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Rule snapshot mismatch");
    }

    @Test
    void shouldRejectStaleValidationForOlderCandidateVersion() {
        CompartmentDto comp = new CompartmentDto("comp-stale", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-stale", 1, "snap-stale", comp);

        ItemPlacementDto item = new ItemPlacementDto("pl-s1", "item-s1", 1, "3", "UN1203", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT", false, 0.0);
        CandidateLayoutDto candidateV2 = new CandidateLayoutDto(
                "layout-stale", 2, "comp-stale", null, "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item)
        );
        AssessmentEntity ready = workflowService.recordCandidateLayout(assessment.getId(), candidateV2);
        assertThat(ready.getCandidateLayoutVersion()).isEqualTo(2);

        // Stale validation belongs to candidate version 1
        ValidationResultDto staleVal = new ValidationResultDto("val-stale-v1", true, "1.0.0", 1, Collections.emptyList());
        staleVal.setCandidateLayoutId("layout-stale");
        staleVal.setCandidateLayoutVersion(1);
        staleVal.setCandidateContentDigest(ready.getCandidateContentDigest());
        staleVal.setRuleSnapshotId("snap-stale");
        org.mockito.Mockito.when(validationClient.getValidationRun("val-stale-v1")).thenReturn(java.util.Optional.of(staleVal));

        assertThatThrownBy(() -> workflowService.recordValidationOutcome(assessment.getId(), staleVal))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("candidate version [1] but assessment is at version [2]");
    }

    @Test
    void shouldRejectEmptyCandidateLayoutClaimingOptimalOrFeasible() {
        CompartmentDto comp = new CompartmentDto("comp-empty", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-empty", 1, "snap-empty", comp);

        // Falsely representing an empty layout as FEASIBLE
        CandidateLayoutDto emptyFeasible = new CandidateLayoutDto(
                "layout-empty-1", 1, "comp-empty", null, "FEASIBLE", "HEURISTIC_EP_3D_V1", Collections.emptyList()
        );
        assertThatThrownBy(() -> workflowService.recordCandidateLayout(assessment.getId(), emptyFeasible))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Candidate layout cannot have empty placements with solverStatus=FEASIBLE");

        // Falsely representing an empty layout as OPTIMAL
        CandidateLayoutDto emptyOptimal = new CandidateLayoutDto(
                "layout-empty-2", 1, "comp-empty", null, "OPTIMAL", "HEURISTIC_EP_3D_V1", Collections.emptyList()
        );
        assertThatThrownBy(() -> workflowService.recordCandidateLayout(assessment.getId(), emptyOptimal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Candidate layout cannot have empty placements with solverStatus=OPTIMAL");
    }

    @Test
    void shouldHandleDuplicateValidationRecordIdempotentlyWithoutSideEffects() {
        CompartmentDto comp = new CompartmentDto("comp-dup", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-dup", 1, "snap-dup", comp);

        ItemPlacementDto item = new ItemPlacementDto("pl-dup", "item-dup", 1, "3", "UN1203", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT", false, 0.0);
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-dup", 1, "comp-dup", null, "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item)
        );
        AssessmentEntity ready = workflowService.recordCandidateLayout(assessment.getId(), candidate);

        ValidationResultDto val = new ValidationResultDto("val-dup-001", true, "1.0.0", 1, Collections.emptyList());
        val.setCandidateLayoutId("layout-dup");
        val.setCandidateLayoutVersion(1);
        val.setCandidateContentDigest(ready.getCandidateContentDigest());
        val.setRuleSnapshotId("snap-dup");
        org.mockito.Mockito.when(validationClient.getValidationRun("val-dup-001")).thenReturn(java.util.Optional.of(val));

        // First execution
        AssessmentEntity first = workflowService.recordValidationOutcome(assessment.getId(), val);
        assertThat(first.getWorkflowStatus()).isEqualTo("PENDING_APPROVAL");

        // Retry / Duplicate execution
        AssessmentEntity second = workflowService.recordValidationOutcome(assessment.getId(), val);
        assertThat(second.getWorkflowStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(second.getValidationId()).isEqualTo("val-dup-001");
    }

    @Test
    void shouldRejectCandidateLayoutWhenSuppliedDigestContradictsCanonicalCalculation() {
        CompartmentDto comp = new CompartmentDto("comp-tampered", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-tampered", 1, "snap-tampered", comp);

        ItemPlacementDto item = new ItemPlacementDto("pl-t1", "item-t1", 1, "3", "UN1203", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT", false, 0.0);
        // Provide tampered digest that does not match canonical calculation
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-tampered", 1, "comp-tampered", "0000000000000000000000000000000000000000000000000000000000000000",
                "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(item)
        );

        assertThatThrownBy(() -> workflowService.recordCandidateLayout(assessment.getId(), candidate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Candidate contentDigest mismatch");
    }
}
