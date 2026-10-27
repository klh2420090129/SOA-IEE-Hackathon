package org.soa8.freight.optimization.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persisted record of processed Kafka results for idempotency and deduplication across restarts.
 */
@Entity
@Table(name = "optimization_processed_messages")
public class ProcessedMessageEntity {
    @Id
    private String messageId; // resultId or correlationId

    private String jobId;
    private String attemptId;
    private Instant processedAt;

    public ProcessedMessageEntity() {}

    public ProcessedMessageEntity(String messageId, String jobId, String attemptId) {
        this.messageId = messageId;
        this.jobId = jobId;
        this.attemptId = attemptId;
        this.processedAt = Instant.now();
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
