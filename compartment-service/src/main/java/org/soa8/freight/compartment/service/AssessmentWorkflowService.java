package org.soa8.freight.compartment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.soa8.freight.common.constants.EventTopics;
import org.soa8.freight.common.events.CargoLoadRejectedPayload;
import org.soa8.freight.common.events.DomainEvent;
import org.soa8.freight.common.model.*;
import org.soa8.freight.common.util.DigestUtils;
import org.soa8.freight.compartment.model.AssessmentEntity;
import org.soa8.freight.compartment.model.CandidateLayoutEntity;
import org.soa8.freight.compartment.model.OutboxEventEntity;
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
    private final ObjectMapper objectMapper;

    public AssessmentWorkflowService(AssessmentRepository assessmentRepository,
                                     CandidateLayoutRepository layoutRepository,
                                     OutboxEventRepository outboxEventRepository,
                                     ObjectMapper objectMapper) {
        this.assessmentRepository = assessmentRepository;
        this.layoutRepository = layoutRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
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

        try {
            String placementsJson = objectMapper.writeValueAsString(candidate.getPlacements());
            String computedDigest = DigestUtils.sha256Hex(placementsJson);

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

        assessment.setValidationId(validation.getValidationId());
        assessment.setValidationPassed(validation.isPassed());

        if (validation.isPassed()) {
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

        if (!assessment.isValidationPassed()) {
            throw new IllegalStateException("Cannot approve candidate: independent validation has not passed");
        }

        if (assessment.getCandidateLayoutVersion() != expectedLayoutVersion) {
            throw new IllegalStateException("Candidate layout version mismatch. Stale approval rejected.");
        }

        if (expectedDigest != null && !expectedDigest.equals(assessment.getCandidateContentDigest())) {
            throw new IllegalStateException("Candidate digest mismatch. Layout has changed since inspection.");
        }

        assessment.setApprovalDecision("APPROVED");
        assessment.setDecidedBy(plannerId);
        assessment.setDecisionNotes(notes);
        assessment.setDecisionTimestamp(Instant.now());
        assessment.setWorkflowStatus("APPROVED");
        assessment.setUpdatedAt(Instant.now());

        return assessmentRepository.save(assessment);
    }

    @Transactional
    public AssessmentEntity rejectCandidate(String assessmentId, int layoutVersion,
                                           String plannerId, String reasonCode, String notes) {
        AssessmentEntity assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + assessmentId));

        // Permitted states for rejection: CANDIDATE_READY, PENDING_APPROVAL, VALIDATION_FAILED
        assessment.setApprovalDecision("REJECTED");
        assessment.setDecidedBy(plannerId);
        assessment.setDecisionReasonCode(reasonCode);
        assessment.setDecisionNotes(notes);
        assessment.setDecisionTimestamp(Instant.now());
        assessment.setWorkflowStatus("REJECTED");
        assessment.setUpdatedAt(Instant.now());

        AssessmentEntity updated = assessmentRepository.save(assessment);

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
                UUID.randomUUID().toString(),
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
