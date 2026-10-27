package org.soa8.freight.optimization.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.soa8.freight.optimization.model.OptimizationOutboxEntity;
import org.soa8.freight.optimization.repository.OptimizationOutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transactional Outbox Publisher for Optimization Commands.
 * Dispatches pending commands to Kafka topic soa.s06.t08.optimization.commands.
 */
@Service
public class OptimizationOutboxPublisherService {
    private static final Logger log = LoggerFactory.getLogger(OptimizationOutboxPublisherService.class);

    private final OptimizationOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OptimizationOutboxPublisherService(OptimizationOutboxRepository outboxRepository,
                                             KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:1500}")
    @Transactional
    public void publishPendingCommands() {
        List<OptimizationOutboxEntity> pending = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        for (OptimizationOutboxEntity entry : pending) {
            try {
                // Keyed by aggregateId (jobId) to guarantee ordering per job partition
                kafkaTemplate.send(entry.getTopic(), entry.getAggregateId(), entry.getPayloadJson());
                entry.setStatus("PUBLISHED");
                entry.setPublishedAt(Instant.now());
                outboxRepository.save(entry);
                log.info("Dispatched optimization outbox command [{}] for job [{}] to topic [{}]",
                        entry.getEventId(), entry.getAggregateId(), entry.getTopic());
            } catch (Exception e) {
                entry.setRetryCount(entry.getRetryCount() + 1);
                if (entry.getRetryCount() >= 5) {
                    entry.setStatus("FAILED");
                    log.error("Optimization outbox command [{}] exceeded max retries. Marking FAILED.", entry.getEventId(), e);
                } else {
                    log.warn("Failed to publish outbox command [{}]. Retry #{}. Error: {}",
                            entry.getEventId(), entry.getRetryCount(), e.getMessage());
                }
                outboxRepository.save(entry);
            }
        }
    }
}
