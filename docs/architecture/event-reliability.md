# Event Reliability & Kafka Architecture

## 1. Domain Event Topic

- **Topic Name:** `soa.s06.t08.events`
- **Primary Event:** `CargoLoadRejected`
- **Owner Service:** `compartment-service`
- **Consumer Service:** `cargo-declaration-service` (maintains workflow projection)

---

## 2. Event Envelope & Payload Schema

```json
{
  "eventId": "a7b3c2d1-e5f6-4a1b-8c2d-3e4f5a6b7c8d",
  "eventType": "CargoLoadRejected",
  "aggregateId": "d1c2b3a4-5f6e-7a8b-9c0d-1e2f3a4b5c6d",
  "aggregateType": "Assessment",
  "schemaVersion": "1.0.0",
  "occurredAt": "2026-10-27T13:00:00Z",
  "correlationId": "corr-987654321",
  "payload": {
    "assessmentId": "d1c2b3a4-5f6e-7a8b-9c0d-1e2f3a4b5c6d",
    "declarationId": "b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e",
    "declarationVersion": 1,
    "layoutVersion": 1,
    "layoutDigest": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "ruleSnapshotId": "snap-2026-q4-v1",
    "rejectedBy": "planner-uuid-1",
    "rejectionReasonCode": "HAZARD_SEGREGATION_VIOLATION",
    "rejectionNotes": "Explosive Class 1.1 cannot co-load with Flammable Liquid Class 3"
  }
}
```

---

## 3. Reliability & Delivery Controls

1. **Transactional Outbox Pattern:**
   - Instead of direct fire-and-forget Kafka calls within business transactions, the `compartment-service` inserts an `OutboxEvent` record in the local database transaction.
   - An asynchronous publisher scans and publishes pending outbox records to Kafka, guaranteeing no dual-write inconsistencies.
2. **Kafka Partition Key:**
   - The Kafka record key is set to `aggregateId` (the `assessmentId`), ensuring ordered delivery of events pertaining to the same aggregate.
3. **Idempotent Consumer:**
   - The `cargo-declaration-service` tracks processed event IDs in a `consumed_events` table.
   - If a duplicate event arrives (e.g., due to network replay or rebalancing), it is acknowledged and ignored without repeating the business projection update.
4. **Dead-Letter Queue (DLQ):**
   - Events failing deserialization or payload validation are routed to `soa.s06.t08.events.DLQ` after bounded retries (3 attempts).
