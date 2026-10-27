package org.soa8.freight.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.soa8.freight.auth.security.JwtProvider;
import org.soa8.freight.common.constants.EventTopics;
import org.soa8.freight.common.events.CargoLoadRejectedPayload;
import org.soa8.freight.common.events.DomainEvent;
import org.soa8.freight.common.model.*;
import org.soa8.freight.compartment.model.AssessmentEntity;
import org.soa8.freight.compartment.model.OutboxEventEntity;
import org.soa8.freight.compartment.repository.OutboxEventRepository;
import org.soa8.freight.compartment.service.AssessmentWorkflowService;
import org.soa8.freight.declaration.model.CargoDeclaration;
import org.soa8.freight.declaration.model.DeclarationWorkflowProjection;
import org.soa8.freight.declaration.repository.CargoDeclarationRepository;
import org.soa8.freight.declaration.repository.DeclarationWorkflowProjectionRepository;
import org.soa8.freight.declaration.service.DeclarationWorkflowService;
import org.soa8.freight.optimization.stub.ControlledOptimizerStub;
import org.soa8.freight.rules.repository.CompatibilityRuleRepository;
import org.soa8.freight.rules.repository.RuleSnapshotRepository;
import org.soa8.freight.validation.engine.IndependentValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-End Vertical Slice Integration Test proving the complete workflow:
 * 1. Shipper Authentication & Role Verification
 * 2. Declaration Creation & Persistence
 * 3. Pinned Rule Snapshot Loading
 * 4. Assessment Creation bound to Declaration & Rule Snapshot
 * 5. Deterministic Optimization via Controlled Optimizer Stub (verifying PROVENANCE: CONTROLLED_STUB)
 * 6. Independent Candidate Validation (Zero-trust geometric & hazard evaluation)
 * 7. Authorized Load Planner Approval/Rejection Gate
 * 8. Domain Event Generation (CargoLoadRejected) into Transactional Outbox
 * 9. Idempotent Consumer Downstream Projection Update in CargoDeclaration
 * 10. Negative Test Cases (Unvalidated Approval Block, Pairwise Overlap, Hazard Violation)
 */
@SpringBootTest(
    classes = {
        org.soa8.freight.auth.AuthServiceApplication.class,
        org.soa8.freight.declaration.CargoDeclarationApplication.class,
        org.soa8.freight.rules.CompatibilityRuleApplication.class,
        org.soa8.freight.compartment.CompartmentApplication.class,
        org.soa8.freight.optimization.OptimizationApplication.class,
        org.soa8.freight.validation.ValidationApplication.class
    },
    properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.listener.auto-startup=false"
    }
)
class EndToEndVerticalSliceIntegrationTest {

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private CargoDeclarationRepository declarationRepository;

    @Autowired
    private RuleSnapshotRepository ruleSnapshotRepository;

    @Autowired
    private AssessmentWorkflowService assessmentWorkflowService;

    @Autowired
    private ControlledOptimizerStub optimizerStub;

    @Autowired
    private IndependentValidator validator;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DeclarationWorkflowService workflowService;

    @Autowired
    private DeclarationWorkflowProjectionRepository projectionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Complete Milestone 1 Vertical Slice Workflow Verification")
    void testCompleteVerticalSliceWorkflow() throws Exception {
        // Step 1: Shipper Authentication
        String shipperToken = jwtProvider.generateToken("shipper-uid-1", "shipper@soa8.org", Set.of("ROLE_SHIPPER"));
        var shipperClaims = jwtProvider.validateAndGetClaims(shipperToken);
        assertThat(shipperClaims.getSubject()).isEqualTo("shipper-uid-1");
        List<String> roles = shipperClaims.get("roles", List.class);
        assertThat(roles).contains("ROLE_SHIPPER");

        // Step 2: Create and Persist Cargo Declaration
        CargoDeclaration declaration = new CargoDeclaration("decl-int-001", "REF-INT-001", "shipper-uid-1", "Mumbai", "Delhi");
        declarationRepository.save(declaration);
        assertThat(declarationRepository.findById("decl-int-001")).isPresent();

        // Step 3: Load Pinned Compatibility Rule Snapshot
        assertThat(ruleSnapshotRepository.findById("snap-2026-q4-v1")).isPresent();

        // Step 4: Create Assessment bound to exact declaration and rule snapshot
        CompartmentDto comp = new CompartmentDto("comp-trailer-1", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = assessmentWorkflowService.createAssessment(
                "decl-int-001", 1, "snap-2026-q4-v1", comp
        );
        assertThat(assessment.getWorkflowStatus()).isEqualTo("DRAFT");

        // Step 5: Dispatch Controlled Optimizer Stub & Verify Provenance
        CargoItemDto item1 = new CargoItemDto("it-solvent", "Solvent", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1);
        CargoItemDto item2 = new CargoItemDto("it-detonator", "Detonator", "1", "UN0004", 800, 800, 800, 150, true, 300, 1);
        CandidateLayoutDto candidate = optimizerStub.generateControlledStubLayout(
                comp.getCompartmentId(), comp.getLengthMm(), comp.getWidthMm(), comp.getHeightMm(),
                List.of(item1, item2)
        );

        // STUB HONESTY CHECK: Must strictly be CONTROLLED_STUB
        assertThat(candidate.getProvenance()).isEqualTo("CONTROLLED_STUB");
        assertThat(candidate.getSolverStatus()).isEqualTo("FEASIBLE");

        // Record Candidate in Assessment
        assessment = assessmentWorkflowService.recordCandidateLayout(assessment.getId(), candidate);
        assertThat(assessment.getWorkflowStatus()).isEqualTo("CANDIDATE_READY");
        String candidateDigest = assessment.getCandidateContentDigest();
        assertThat(candidateDigest).isNotBlank();

        // Step 6: Negative Security Check - Load Planner Cannot Approve Unvalidated Candidate
        String assessmentId = assessment.getId();
        assertThatThrownBy(() -> assessmentWorkflowService.approveCandidate(
                assessmentId, 1, candidateDigest, "planner-001", "Premature"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Assessment cannot be approved in current state: CANDIDATE_READY");

        // Step 7: Independent Validation (Zero-trust evaluation)
        ValidationResultDto validationResult = validator.validate(candidate, comp, null);
        assertThat(validationResult.isPassed()).isTrue();
        assertThat(validationResult.getValidatorVersion()).isEqualTo("1.0.0-m1");

        // Record Validation Result
        assessment = assessmentWorkflowService.recordValidationOutcome(assessment.getId(), validationResult);
        assertThat(assessment.getWorkflowStatus()).isEqualTo("PENDING_APPROVAL");

        // Step 8: Load Planner Rejection
        assessment = assessmentWorkflowService.rejectCandidate(
                assessment.getId(), 1, "planner-001", "OPERATIONAL_REJECTION", "Manual hold for inspection"
        );
        assertThat(assessment.getWorkflowStatus()).isEqualTo("REJECTED");

        // Step 9: Verify Transactional Outbox published CargoLoadRejected event
        List<OutboxEventEntity> outbox = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        assertThat(outbox).isNotEmpty();
        OutboxEventEntity eventEntity = outbox.stream()
                .filter(e -> assessmentId.equals(e.getAggregateId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Outbox event not found for assessment " + assessmentId));
        assertThat(eventEntity.getEventType()).isEqualTo("CargoLoadRejected");

        // Step 10: Downstream Consumer Event Processing & Idempotency
        DomainEvent<CargoLoadRejectedPayload> event = objectMapper.readValue(
                eventEntity.getPayloadJson(),
                objectMapper.getTypeFactory().constructParametricType(DomainEvent.class, CargoLoadRejectedPayload.class)
        );

        boolean processedFirst = workflowService.processCargoLoadRejected(event);
        assertThat(processedFirst).isTrue();

        // Verify downstream projection persisted in CargoDeclaration
        DeclarationWorkflowProjection projection = projectionRepository.findById(event.getPayload().getDeclarationId()).orElseThrow();
        assertThat(projection.getWorkflowStatus()).isEqualTo("REJECTED");
        assertThat(projection.getRejectionReasonCode()).isEqualTo("OPERATIONAL_REJECTION");

        // Duplicate delivery (Kafka replay simulation)
        boolean processedDuplicate = workflowService.processCargoLoadRejected(event);
        assertThat(processedDuplicate).isFalse(); // Exactly once / idempotent behavior
    }

    @Test
    @DisplayName("Negative Test Case: Physical Overlap & Hazard Segregation Detection")
    void testNegativeValidationCases() {
        CompartmentDto comp = new CompartmentDto("comp-trailer-1", "Standard Trailer", 12000, 2400, 2600, 25000);

        // Case A: Geometric Overlap
        ItemPlacementDto p1 = new ItemPlacementDto("p1", "it1", 0, "3", "UN1993", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT");
        ItemPlacementDto p2 = new ItemPlacementDto("p2", "it2", 0, "8", "UN1789", 500, 500, 500, 1000, 1000, 1000, 500, "DEFAULT");
        CandidateLayoutDto overlapLayout = new CandidateLayoutDto("l-overlap", 1, "comp-trailer-1", "d1", "FEASIBLE", "CONTROLLED_STUB", List.of(p1, p2));

        ValidationResultDto overlapRes = validator.validate(overlapLayout, comp, null);
        assertThat(overlapRes.isPassed()).isFalse();
        assertThat(overlapRes.getFindings()).anyMatch(f -> "OVERLAP_DETECTED".equals(f.getCode()));

        // Case B: Hazard Incompatibility Segregation Distance (< 3000mm)
        ItemPlacementDto haz1 = new ItemPlacementDto("h1", "it1", 0, "1", "UN0004", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT");
        ItemPlacementDto haz2 = new ItemPlacementDto("h2", "it2", 0, "3", "UN1993", 1500, 0, 0, 1000, 1000, 1000, 500, "DEFAULT");
        CandidateLayoutDto hazardLayout = new CandidateLayoutDto("l-haz", 1, "comp-trailer-1", "d2", "FEASIBLE", "CONTROLLED_STUB", List.of(haz1, haz2));

        CompatibilityRuleDto rule = new CompatibilityRuleDto("r1", "CMV-HAZ-1-3", "1", "3", "SEPARATED_BY_DISTANCE", 3000, "Min 3000mm separation", "INDIA");
        RuleSnapshotDto snapshot = new RuleSnapshotDto("snap-1", "INDIA", null, "PUBLISHED", "digest", List.of(rule));

        ValidationResultDto hazardRes = validator.validate(hazardLayout, comp, snapshot);
        assertThat(hazardRes.isPassed()).isFalse();
        assertThat(hazardRes.getFindings()).anyMatch(f -> "HAZARD_SEGREGATION_VIOLATION".equals(f.getCode()));

        // Case C: Unsupported Stacking Failure and Approval Blocking
        ItemPlacementDto floating = new ItemPlacementDto("f1", "it-float", 0, "NON_HAZ", "UN0000", 0, 0, 800, 1000, 1000, 1000, 500, "DEFAULT", true, 500.0);
        CandidateLayoutDto floatLayout = new CandidateLayoutDto("l-float", 1, "comp-trailer-1", "d3", "FEASIBLE", "HEURISTIC_EP_3D_V1", List.of(floating));
        ValidationResultDto floatRes = validator.validate(floatLayout, comp, null);
        assertThat(floatRes.isPassed()).isFalse();
        assertThat(floatRes.getFindings()).anyMatch(f -> "UNSUPPORTED_CARGO".equals(f.getCode()));

        // Verify that recording a failed validation marks workflow as VALIDATION_FAILED and blocks approval
        AssessmentEntity floatAssessment = assessmentWorkflowService.createAssessment("decl-float-01", 1, "snap-2026-q4-v1", comp);
        floatAssessment = assessmentWorkflowService.recordCandidateLayout(floatAssessment.getId(), floatLayout);
        floatAssessment = assessmentWorkflowService.recordValidationOutcome(floatAssessment.getId(), floatRes);
        assertThat(floatAssessment.getWorkflowStatus()).isEqualTo("VALIDATION_FAILED");

        String floatAsmtId = floatAssessment.getId();
        String floatDigest = floatAssessment.getCandidateContentDigest();
        assertThatThrownBy(() -> assessmentWorkflowService.approveCandidate(
                floatAsmtId, 1, floatDigest, "planner-001", "Attempted illegal approval"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Assessment cannot be approved in current state: VALIDATION_FAILED");
    }
}
