package org.soa8.freight.common.events;

import java.time.Instant;
import java.util.UUID;

public class DomainEvent<T> {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private String schemaVersion;
    private Instant occurredAt;
    private String correlationId;
    private T payload;

    public DomainEvent() {
    }

    public DomainEvent(String eventType, String aggregateId, String aggregateType, String schemaVersion, String correlationId, T payload) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.schemaVersion = schemaVersion;
        this.occurredAt = Instant.now();
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        this.payload = payload;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }
}
