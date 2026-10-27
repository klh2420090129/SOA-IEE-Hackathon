package org.soa8.freight.compartment.service;

import org.soa8.freight.compartment.model.OutboxEventEntity;
import org.soa8.freight.compartment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxPublisherService {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherService(OutboxEventRepository outboxEventRepository,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        for (OutboxEventEntity event : pendingEvents) {
            try {
                // Keyed by aggregateId to preserve partition order
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayloadJson());
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                log.info("Published outbox event [{}] to topic [{}]", event.getEventId(), event.getTopic());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= 5) {
                    event.setStatus("FAILED");
                    log.error("Outbox event [{}] exceeded max retries. Marking FAILED.", event.getEventId(), e);
                } else {
                    log.warn("Failed to publish outbox event [{}]. Will retry. Error: {}", event.getEventId(), e.getMessage());
                }
                outboxEventRepository.save(event);
            }
        }
    }
}
