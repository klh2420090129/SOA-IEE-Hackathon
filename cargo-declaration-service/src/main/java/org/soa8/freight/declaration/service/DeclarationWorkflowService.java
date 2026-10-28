package org.soa8.freight.declaration.service;

import org.soa8.freight.common.events.CargoLoadRejectedPayload;
import org.soa8.freight.common.events.DomainEvent;
import org.soa8.freight.declaration.model.ConsumedEvent;
import org.soa8.freight.declaration.model.DeclarationWorkflowProjection;
import org.soa8.freight.declaration.repository.CargoDeclarationRepository;
import org.soa8.freight.declaration.repository.ConsumedEventRepository;
import org.soa8.freight.declaration.repository.DeclarationWorkflowProjectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeclarationWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(DeclarationWorkflowService.class);

    private final DeclarationWorkflowProjectionRepository projectionRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final CargoDeclarationRepository declarationRepository;

    public DeclarationWorkflowService(DeclarationWorkflowProjectionRepository projectionRepository,
                                      ConsumedEventRepository consumedEventRepository,
                                      CargoDeclarationRepository declarationRepository) {
        this.projectionRepository = projectionRepository;
        this.consumedEventRepository = consumedEventRepository;
        this.declarationRepository = declarationRepository;
    }

    @Transactional
    public boolean processCargoLoadRejected(DomainEvent<CargoLoadRejectedPayload> event) {
        if (event == null || event.getEventId() == null || event.getPayload() == null) {
            log.warn("Invalid event envelope or payload received. Discarding.");
            return false;
        }

        // Idempotency check: has this event already been processed?
        if (consumedEventRepository.existsById(event.getEventId())) {
            log.info("Duplicate event [{}] already processed. Skipping business action.", event.getEventId());
            return false;
        }

        CargoLoadRejectedPayload payload = event.getPayload();
        log.info("Processing CargoLoadRejected for declaration [{}] assessment [{}]",
                payload.getDeclarationId(), payload.getAssessmentId());

        // Update declaration main status if present
        declarationRepository.findById(payload.getDeclarationId()).ifPresent(decl -> {
            decl.setStatus("REJECTED");
            declarationRepository.save(decl);
        });

        // Persist downstream workflow projection
        DeclarationWorkflowProjection projection = projectionRepository.findById(payload.getDeclarationId())
                .orElse(new DeclarationWorkflowProjection());

        projection.setDeclarationId(payload.getDeclarationId());
        projection.setAssessmentId(payload.getAssessmentId());
        projection.setWorkflowStatus("REJECTED");
        projection.setRejectionReasonCode(payload.getRejectionReasonCode());
        projection.setRejectionNotes(payload.getRejectionNotes());
        projection.setLastUpdated(event.getOccurredAt());
        projectionRepository.save(projection);

        // Record consumed event ID
        consumedEventRepository.save(new ConsumedEvent(event.getEventId(), event.getEventType()));
        return true;
    }

    @Transactional
    public boolean processCargoLoadApproved(DomainEvent<org.soa8.freight.common.events.CargoLoadApprovedPayload> event) {
        if (event == null || event.getEventId() == null || event.getPayload() == null) {
            log.warn("Invalid event envelope or payload received. Discarding.");
            return false;
        }

        // Idempotency check: has this event already been processed?
        if (consumedEventRepository.existsById(event.getEventId())) {
            log.info("Duplicate event [{}] already processed. Skipping business action.", event.getEventId());
            return false;
        }

        org.soa8.freight.common.events.CargoLoadApprovedPayload payload = event.getPayload();
        log.info("Processing CargoLoadApproved for declaration [{}] assessment [{}]",
                payload.getDeclarationId(), payload.getAssessmentId());

        // Update declaration main status if present
        declarationRepository.findById(payload.getDeclarationId()).ifPresent(decl -> {
            decl.setStatus("APPROVED");
            declarationRepository.save(decl);
        });

        // Persist downstream workflow projection
        DeclarationWorkflowProjection projection = projectionRepository.findById(payload.getDeclarationId())
                .orElse(new DeclarationWorkflowProjection());

        projection.setDeclarationId(payload.getDeclarationId());
        projection.setAssessmentId(payload.getAssessmentId());
        projection.setWorkflowStatus("APPROVED");
        projection.setRejectionReasonCode(null);
        projection.setRejectionNotes(payload.getApprovalNotes());
        projection.setLastUpdated(event.getOccurredAt());
        projectionRepository.save(projection);

        // Record consumed event ID
        consumedEventRepository.save(new ConsumedEvent(event.getEventId(), event.getEventType()));
        return true;
    }
}
