package org.soa8.freight.declaration.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.soa8.freight.common.constants.EventTopics;
import org.soa8.freight.common.events.CargoLoadRejectedPayload;
import org.soa8.freight.common.events.DomainEvent;
import org.soa8.freight.declaration.service.DeclarationWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CargoEventListener {
    private static final Logger log = LoggerFactory.getLogger(CargoEventListener.class);

    private final DeclarationWorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public CargoEventListener(DeclarationWorkflowService workflowService, ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = EventTopics.CARGO_EVENTS_TOPIC, groupId = "cargo-declaration-group")
    public void onEventReceived(String message) {
        log.info("Received Kafka message: {}", message);
        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(message);
            String eventType = rootNode.has("eventType") ? rootNode.get("eventType").asText() : "";

            if (EventTopics.EVENT_TYPE_CARGO_LOAD_REJECTED.equals(eventType)) {
                DomainEvent<CargoLoadRejectedPayload> event = objectMapper.readValue(
                        message, new TypeReference<DomainEvent<CargoLoadRejectedPayload>>() {}
                );
                workflowService.processCargoLoadRejected(event);
            } else if (EventTopics.EVENT_TYPE_CARGO_LOAD_APPROVED.equals(eventType)) {
                DomainEvent<org.soa8.freight.common.events.CargoLoadApprovedPayload> event = objectMapper.readValue(
                        message, new TypeReference<DomainEvent<org.soa8.freight.common.events.CargoLoadApprovedPayload>>() {}
                );
                workflowService.processCargoLoadApproved(event);
            } else {
                log.info("Ignoring event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to parse event message: {}", message, e);
        }
    }
}
