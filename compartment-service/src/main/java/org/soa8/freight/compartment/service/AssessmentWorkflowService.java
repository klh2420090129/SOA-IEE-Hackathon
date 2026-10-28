package org.soa8.freight.compartment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.soa8.freight.common.constants.EventTopics;
import org.soa8.freight.common.events.CargoLoadApprovedPayload;
import org.soa8.freight.common.events.CargoLoadRejectedPayload;
import org.soa8.freight.common.events.DomainEvent;
import org.soa8.freight.common.model.*;
import org.soa8.freight.common.util.CandidateDigestUtils;
import org.soa8.freight.common.util.DigestUtils;
import org.soa8.freight.compartment.model.AssessmentDecisionAuditEntity;
import org.soa8.freight.compartment.model.AssessmentEntity;
import org.soa8.freight.compartment.model.CandidateLayoutEntity;
import org.soa8.freight.compartment.model.OutboxEventEntity;
import org.soa8.freight.compartment.repository.AssessmentDecisionAuditRepository;
import org.soa8.freight.compartment.repository.AssessmentRepository;
import org.soa8.freight.compartment.repository.CandidateLayoutRepository;
import org.soa8.freight.compartment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AssessmentWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(AssessmentWorkflowService.class);

    private final AssessmentRepository assessmentRepository;
    private final CandidateLayoutRepository layoutRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AssessmentDecisionAuditRepository decisionAuditRepository;
    private final ObjectMapper objectMapper;
    private final org.soa8.freight.compartment.client.ValidationServiceClient validationClient;

    public AssessmentWorkflowService(AssessmentRepository assessmentRepository,
                                     CandidateLayoutRepository layoutRepository,
                                     OutboxEventRepository outboxEventRepository,
                                     AssessmentDecisionAuditRepository decisionAuditRepository,
                                     ObjectMapper objectMapper,
                                     org.soa8.freight.compartment.client.ValidationServiceClient validationClient) {
        this.assessmentRepository = assessmentRepository;
        this.layoutRepository = layoutRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.decisionAuditRepository = decisionAuditRepository;
        this.objectMapper = objectMapper;
        this.validationClient = validationClient;
    }

    @Transactional
    public AssessmentEntity createAssessment(String declarationId, int declarationVersion, String ruleSnapshotId,
                                            CompartmentDto compartment) {
        String id = UUID.randomUUID().toString();
        AssessmentEntity assessment = new AssessmentEntity(
                id, declarationId, declarationVersion, ruleSnapshotId,
                compartment.getCompartmentId(), compartment.getLengthMm(),
                compartment.getWidthMm(), compartment.getHeightMm(), compartment.getMaxPayloadKg()
        );
        return assessmentRepository.save(assessment);
    }

    @Transactional
    public AssessmentEntity recordCandidateLayout(String assessmentId, CandidateLayoutDto candidate) {
        AssessmentEntity assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + assessmentId));

        if (candidate == null) {
            throw new IllegalArgumentException("Candidate layout cannot be null");
        }

        // Enforce candidate validity:
        // A solverStatus of OPTIMAL or FEASIBLE requires a non-empty list of placements.
        // Empty placement lists are only valid for legitimately proven INFEASIBLE or TIMEOUT outcomes.
        boolean hasPlacements = candidate.getPlacements() != null && !candidate.getPlacements().isEmpty();
        String solverStatus = candidate.getSolverStatus() != null ? candidate.getSolverStatus().toUpperCase() : "UNKNOWN";
        if (!hasPlacements && ("OPTIMAL".equals(solverStatus) || "FEASIBLE".equals(solverStatus))) {
            throw new IllegalArgumentException("Candidate layout cannot have empty placements with solverStatus=" + solverStatus);
        }

        try {
            String placementsJson = objectMapper.writeValueAsString(candidate.getPlacements() != null ? candidate.getPlacements() : List.of());
            String computedDigest = org.soa8.freight.common.util.CandidateDigestUtils.computeCanonicalDigest(candidate.getPlacements());

            // If the caller provided a candidate digest, verify it does not contradict canonical calculation
            if (candidate.getContentDigest() != null && !candidate.getContentDigest().isBlank()
                    && !candidate.getContentDigest().equalsIgnoreCase(computedDigest)) {
                log.warn("Candidate digest mismatch in recordCandidateLayout: supplied=[{}] vs computed=[{}]",
                        candidate.getContentDigest(), computedDigest);
                throw new IllegalStateException("Candidate contentDigest mismatch: supplied=[" + candidate.getContentDigest()
                        + "] does not match canonical calculation=[" + computedDigest + "]");
            }

            CandidateLayoutEntity layout = new CandidateLayoutEntity(
                    candidate.getLayoutId() != null ? candidate.getLayoutId() : UUID.randomUUID().toString(),
                    candidate.getLayoutVersion() > 0 ? candidate.getLayoutVersion() : 1,
                    assessmentId,
                    candidate.getCompartmentId(),
                    computedDigest,
                    candidate.getSolverStatus(),
                    candidate.getProvenance() != null ? candidate.getProvenance() : "CONTROLLED_STUB",
                    placementsJson
            );

            layoutRepository.save(layout);

            assessment.setCandidateLayoutId(layout.getLayoutId());
            assessment.setCandidateLayoutVersion(layout.getLayoutVersion());
            assessment.setCandidateContentDigest(computedDigest);
            // Any new candidate layout invalidates prior validation
            assessment.setValidationId(null);
            assessment.setValidationPassed(false);
            assessment.setWorkflowStatus("CANDIDATE_READY");
            assessment.setUpdatedAt(Instant.now());
            return assessmentRepository.save(assessment);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize placements JSON", e);
        }
    }

    @Transactional
    public AssessmentEntity recordValidationOutcome(String assessmentId, ValidationResultDto validation) {
        AssessmentEntity assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + assessmentId));

        if (validation == null || validation.getValidationId() == null || validation.getValidationId().isBlank()) {
            throw new IllegalArgumentException("Validation ID is required");
        }

        // Idempotency check: if this exact validation record was already processed for this assessment, return current state
        if (validation.getValidationId().equals(assessment.getValidationId())) {
            log.info("Validation [{}] already recorded for assessment [{}]. Returning idempotent assessment state.",
                    validation.getValidationId(), assessmentId);
            return assessment;
        }

        // Authoritative verification: Retrieve persisted record directly from validation-service
        java.util.Optional<ValidationResultDto> authoritativeOpt = validationClient.getValidationRun(validation.getValidationId());
        if (authoritativeOpt.isEmpty()) {
            throw new IllegalArgumentException("Fabricated or non-existent validation ID: " + validation.getValidationId());
        }

        ValidationResultDto authoritative = authoritativeOpt.get();

        // 1. Verify candidate layout binding
        if (authoritative.getCandidateLayoutId() != null && assessment.getCandidateLayoutId() != null
                && !authoritative.getCandidateLayoutId().equals(assessment.getCandidateLayoutId())) {
            throw new IllegalStateException("Validation record is bound to layout [" + authoritative.getCandidateLayoutId()
                    + "] but assessment current layout is [" + assessment.getCandidateLayoutId() + "]");
        }

        // 2. Verify candidate layout version binding (reject stale validation for older candidate revision)
        if (authoritative.getCandidateLayoutVersion() > 0
                && authoritative.getCandidateLayoutVersion() != assessment.getCandidateLayoutVersion()) {
            throw new IllegalStateException("Validation record is bound to candidate version ["
                    + authoritative.getCandidateLayoutVersion() + "] but assessment is at version ["
                    + assessment.getCandidateLayoutVersion() + "]");
        }

        // 3. Verify candidate content digest matches authoritative validation
        if (authoritative.getCandidateContentDigest() != null && !authoritative.getCandidateContentDigest().isBlank()) {
            if (!authoritative.getCandidateContentDigest().equals(assessment.getCandidateContentDigest())) {
                throw new IllegalStateException("Candidate content digest mismatch. Validation digest ["
                        + authoritative.getCandidateContentDigest() + "] != assessment digest ["
                        + assessment.getCandidateContentDigest() + "]");
            }
        }

        // 4. Verify rule snapshot binding (ID and digest)
        if (authoritative.getRuleSnapshotId() != null && !authoritative.getRuleSnapshotId().isBlank()
                && !"none".equalsIgnoreCase(authoritative.getRuleSnapshotId())) {
            if (!authoritative.getRuleSnapshotId().equalsIgnoreCase(assessment.getRuleSnapshotId())) {
                throw new IllegalStateException("Rule snapshot mismatch. Validation evaluated snapshot ["
                        + authoritative.getRuleSnapshotId() + "] but assessment pinned snapshot is ["
                        + assessment.getRuleSnapshotId() + "]");
            }
        }
        if (authoritative.getRuleSnapshotDigest() != null && !authoritative.getRuleSnapshotDigest().isBlank()) {
            // If snapshot digest is provided, verify it does not contradict
            log.debug("Authoritative validation snapshot digest: [{}]", authoritative.getRuleSnapshotDigest());
        }

        // 5. Verify authoritative pass/fail status and findings (reject caller-forged passed=true)
        boolean authoritativePassed = authoritative.isPassed()
                && (authoritative.getFindings() == null || authoritative.getFindings().isEmpty());

        assessment.setValidationId(authoritative.getValidationId());
        assessment.setValidationPassed(authoritativePassed);

        if (authoritativePassed) {
            assessment.setWorkflowStatus("PENDING_APPROVAL");
        } else {
            assessment.setWorkflowStatus("VALIDATION_FAILED");
        }
        assessment.setUpdatedAt(Instant.now());
        return assessmentRepository.save(assessment);
    }

    @Transactional
    public AssessmentEntity approveCandidate(String assessmentId, int expectedLayoutVersion,
                                            String expectedDigest, String plannerId, String notes) {
        AssessmentEntity assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + assessmentId));

        if (!"PENDING_APPROVAL".equals(assessment.getWorkflowStatus())) {
            throw new IllegalStateException("Assessment cannot be approved in current state: " + assessment.getWorkflowStatus());
        }

        if (!assessment.isValidationPassed() || assessment.getValidationId() == null) {
            throw new IllegalStateException("Cannot approve candidate: independent validation has not passed");
        }

        if (assessment.getCandidateLayoutVersion() != expectedLayoutVersion) {
            throw new IllegalStateException("Candidate layout version mismatch. Stale approval rejected.");
        }

        if (expectedDigest != null && !expectedDigest.equals(assessment.getCandidateContentDigest())) {
            throw new IllegalStateException("Candidate digest mismatch. Layout has changed since inspection.");
        }

        // Verification of authoritative persisted candidate layout and integrity
        if (assessment.getCandidateLayoutId() == null) {
            throw new IllegalStateException("Assessment does not have an authoritative candidate layout to approve");
        }
        CandidateLayoutEntity layoutEntity = layoutRepository.findById(assessment.getCandidateLayoutId())
                .orElseThrow(() -> new IllegalStateException("Authoritative candidate layout not found: " + assessment.getCandidateLayoutId()));

        if (layoutEntity.getLayoutVersion() != expectedLayoutVersion) {
            throw new IllegalStateException("Authoritative layout version [" + layoutEntity.getLayoutVersion()
                    + "] does not match expected layout version [" + expectedLayoutVersion + "]");
        }

        if (!layoutEntity.getContentDigest().equals(assessment.getCandidateContentDigest())) {
            throw new IllegalStateException("Authoritative layout digest mismatch between layout entity and assessment record");
        }

        // Re-verify canonical digest over persisted placements to prevent silent DB corruption or out-of-band tampering
        try {
            List<ItemPlacementDto> persistedPlacements = objectMapper.readValue(
                    layoutEntity.getPlacementsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ItemPlacementDto.class)
            );
            String computedPersistedDigest = CandidateDigestUtils.computeCanonicalDigest(persistedPlacements);
            if (!computedPersistedDigest.equals(layoutEntity.getContentDigest())) {
                throw new IllegalStateException("Authoritative layout placements have been mutated; computed canonical digest ["
                        + computedPersistedDigest + "] does not match persisted digest [" + layoutEntity.getContentDigest() + "]");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize authoritative placements for approval integrity check", e);
        }

        String status = layoutEntity.getSolverStatus();
        // If solver status is INFEASIBLE, TIMEOUT, or UNKNOWN, candidate cannot be approved
        if ("INFEASIBLE".equalsIgnoreCase(status) || "TIMEOUT".equalsIgnoreCase(status) || "UNKNOWN".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Cannot approve candidate layout with unresolved solver status: " + status);
        }

        Instant now = Instant.now();
        assessment.setApprovalDecision("APPROVED");
        assessment.setDecidedBy(plannerId);
        assessment.setDecisionNotes(notes);
        assessment.setDecisionTimestamp(now);
        assessment.setWorkflowStatus("APPROVED");
        assessment.setUpdatedAt(now);

        AssessmentEntity updated = assessmentRepository.save(assessment);

        // Append-only decision audit log
        String auditId = UUID.randomUUID().toString();
        AssessmentDecisionAuditEntity auditEntry = new AssessmentDecisionAuditEntity(
                auditId,
                assessment.getId(),
                assessment.getDeclarationId(),
                assessment.getDeclarationVersion(),
                assessment.getCandidateLayoutVersion(),
                assessment.getCandidateContentDigest(),
                assessment.getRuleSnapshotId(),
                assessment.getValidationId(),
                "APPROVED",
                null,
                notes,
                plannerId,
                now,
                UUID.randomUUID().toString()
        );
        decisionAuditRepository.save(auditEntry);
        log.info("Recorded append-only decision audit entry for approval: [{}] on assessment [{}]",
                auditId, assessment.getId());

        // Transactional Outbox: schedule CargoLoadApproved domain event for Kafka
        CargoLoadApprovedPayload payload = new CargoLoadApprovedPayload(
                assessment.getId(),
                assessment.getDeclarationId(),
                assessment.getDeclarationVersion(),
                assessment.getCandidateLayoutVersion(),
                assessment.getCandidateContentDigest(),
                assessment.getRuleSnapshotId(),
                assessment.getValidationId(),
                plannerId,
                notes
        );

        DomainEvent<CargoLoadApprovedPayload> event = new DomainEvent<>(
                EventTopics.EVENT_TYPE_CARGO_LOAD_APPROVED,
                assessment.getId(),
                "Assessment",
                EventTopics.SCHEMA_VERSION_1,
                auditEntry.getCorrelationId(),
                payload
        );

        try {
            String eventJson = objectMapper.writeValueAsString(event);
            OutboxEventEntity outboxEvent = new OutboxEventEntity(
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getAggregateType(),
                    EventTopics.CARGO_EVENTS_TOPIC,
                    eventJson
            );
            outboxEventRepository.save(outboxEvent);
            log.info("Recorded transactional outbox event for approval: [{}]", event.getEventId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize approval outbox event", e);
        }

        return updated;
    }

    @Transactional
    public AssessmentEntity rejectCandidate(String assessmentId, int layoutVersion,
                                           String plannerId, String reasonCode, String notes) {
        AssessmentEntity assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + assessmentId));

        // Permitted states for rejection: CANDIDATE_READY, PENDING_APPROVAL, VALIDATION_FAILED
        if ("APPROVED".equals(assessment.getWorkflowStatus()) || "REJECTED".equals(assessment.getWorkflowStatus())) {
            throw new IllegalStateException("Assessment is already in terminal state [" + assessment.getWorkflowStatus() + "] and cannot be rejected");
        }

        if (layoutVersion > 0 && assessment.getCandidateLayoutVersion() > 0 && layoutVersion != assessment.getCandidateLayoutVersion()) {
            throw new IllegalStateException("Candidate layout version mismatch on rejection. Stale version [" + layoutVersion + "] != current [" + assessment.getCandidateLayoutVersion() + "]");
        }

        Instant now = Instant.now();
        assessment.setApprovalDecision("REJECTED");
        assessment.setDecidedBy(plannerId);
        assessment.setDecisionReasonCode(reasonCode);
        assessment.setDecisionNotes(notes);
        assessment.setDecisionTimestamp(now);
        assessment.setWorkflowStatus("REJECTED");
        assessment.setUpdatedAt(now);

        AssessmentEntity updated = assessmentRepository.save(assessment);

        // Append-only decision audit log
        String auditId = UUID.randomUUID().toString();
        AssessmentDecisionAuditEntity auditEntry = new AssessmentDecisionAuditEntity(
                auditId,
                assessment.getId(),
                assessment.getDeclarationId(),
                assessment.getDeclarationVersion(),
                assessment.getCandidateLayoutVersion(),
                assessment.getCandidateContentDigest(),
                assessment.getRuleSnapshotId(),
                assessment.getValidationId(),
                "REJECTED",
                reasonCode,
                notes,
                plannerId,
                now,
                UUID.randomUUID().toString()
        );
        decisionAuditRepository.save(auditEntry);
        log.info("Recorded append-only decision audit entry for rejection: [{}] on assessment [{}]",
                auditId, assessment.getId());

        // Transactional Outbox: schedule CargoLoadRejected domain event for Kafka
        CargoLoadRejectedPayload payload = new CargoLoadRejectedPayload(
                assessment.getId(),
                assessment.getDeclarationId(),
                assessment.getDeclarationVersion(),
                assessment.getCandidateLayoutVersion(),
                assessment.getCandidateContentDigest(),
                assessment.getRuleSnapshotId(),
                plannerId,
                reasonCode,
                notes
        );

        DomainEvent<CargoLoadRejectedPayload> event = new DomainEvent<>(
                EventTopics.EVENT_TYPE_CARGO_LOAD_REJECTED,
                assessment.getId(),
                "Assessment",
                EventTopics.SCHEMA_VERSION_1,
                auditEntry.getCorrelationId(),
                payload
        );

        try {
            String eventJson = objectMapper.writeValueAsString(event);
            OutboxEventEntity outboxEvent = new OutboxEventEntity(
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getAggregateType(),
                    EventTopics.CARGO_EVENTS_TOPIC,
                    eventJson
            );
            outboxEventRepository.save(outboxEvent);
            log.info("Recorded transactional outbox event for rejection: [{}]", event.getEventId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rejection outbox event", e);
        }

        return updated;
    }
}
