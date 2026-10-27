# SOA Team 8 — Milestone 1.5 Live Application Integration Report

**Repository**: `https://github.com/klh2420090129/SOA-IEE-Hackathon.git`  
**Branch**: `feat/milestone-1-vertical-slice`  
**Commit SHA**: `acde2adda7151dbc37988af74a060b08a9ae570c`  
**Timestamp**: 2026-10-27T14:40:00+05:30  

---

## 1. Executive Summary

Milestone 1.5 establishes a verified, genuinely integrated, and runnable distributed platform across Docker infrastructure (MySQL 8.0, Apache Kafka 3.7, Zookeeper), all 7 Spring Boot microservices, and the React/Vite web application. All unit-test passes and live integration runtime evidence are strictly segregated.

All 38 locked architecture decisions were preserved, including database isolation per service, transactional outbox pattern, idempotent Kafka event handling, zero-trust validation, and controlled stub optimization provenance (`CONTROLLED_STUB`).

---

## 2. Infrastructure & Runtime Configuration

### A. Reproducible Runtime Stack

| Component | Port | Host Address | Technology | Runtime Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **MySQL Database** | 3306 | `localhost:3306` | MySQL 8.0 Container (`soa8-mysql`) | 6 isolated databases created on startup (`auth_db`, `cargo_declaration_db`, `compatibility_rules_db`, `compartment_db`, `optimization_db`, `validation_db`). |
| **Apache Kafka** | 9092 | `localhost:9092` | Kafka 3.7 Container (`soa8-kafka`) | Topic `soa.s06.t08.events` created (3 partitions, replication factor 1). |
| **Zookeeper** | 2181 | `localhost:2181` | Zookeeper Container (`soa8-zookeeper`) | Healthy. |
| **Service Discovery** | 8761 | `localhost:8761` | Netflix Eureka Server | All 6 downstream services + API Gateway registered with `UP` status. |
| **API Gateway** | 8080 | `localhost:8080` | Spring Cloud Gateway (Reactive Netty) | Routes configured with load balancer (`lb://`) and `prefer-ip-address: true`. |
| **Auth Service** | 8081 | `localhost:8081` | Spring Boot 3.3.4 (MVC + JPA) | MySQL profile (`auth_db`). Seeded with BCrypt-hashed credentials. |
| **Cargo Declaration**| 8082 | `localhost:8082` | Spring Boot 3.3.4 (MVC + JPA + Kafka Consumer) | MySQL profile (`cargo_declaration_db`). Idempotent consumer updating projections. |
| **Compatibility Rules** | 8083 | `localhost:8083` | Spring Boot 3.3.4 (MVC + JPA) | MySQL profile (`compatibility_rules_db`). Published snapshot `snap-2026-q4-v1`. |
| **Compartment Service** | 8084 | `localhost:8084` | Spring Boot 3.3.4 (MVC + JPA + Kafka Outbox) | MySQL profile (`compartment_db`). Transactional outbox polling scheduled at 5s. |
| **Optimization Service**| 8085 | `localhost:8085` | Spring Boot 3.3.4 (MVC + JPA) | MySQL profile (`optimization_db`). Controlled stub generator (`CONTROLLED_STUB`). |
| **Validation Service** | 8086 | `localhost:8086` | Spring Boot 3.3.4 (MVC + JPA) | MySQL profile (`validation_db`). Independent 3D geometric & hazard evaluator. |
| **Frontend Web App** | 5173 | `localhost:5173` | React 18 + Vite + TypeScript | Asynchronous live HTTP fetch calls to Gateway (`/api/v1/...`). |

---

## 3. Exact Startup and Shutdown Commands

### Exact Startup Commands

```powershell
# 1. Start Docker Engine and Infrastructure Containers
docker compose -f docker-compose.yml up -d
# Wait 10 seconds for MySQL and Kafka initialization

# 2. Build and Package Application Binaries with MySQL Drivers & Parameter Metadata
mvn install -DskipTests

# 3. Start Eureka Service Discovery (Port 8761)
Start-Process java -ArgumentList "-jar", "service-discovery/target/service-discovery-1.0.0-SNAPSHOT-exec.jar"

# 4. Start Core Domain Microservices with MySQL Profile
Start-Process java -ArgumentList "-Dspring.profiles.active=mysql", "-jar", "auth-service/target/auth-service-1.0.0-SNAPSHOT-exec.jar"
Start-Process java -ArgumentList "-Dspring.profiles.active=mysql", "-jar", "cargo-declaration-service/target/cargo-declaration-service-1.0.0-SNAPSHOT-exec.jar"
Start-Process java -ArgumentList "-Dspring.profiles.active=mysql", "-jar", "compatibility-rule-service/target/compatibility-rule-service-1.0.0-SNAPSHOT-exec.jar"
Start-Process java -ArgumentList "-Dspring.profiles.active=mysql", "-jar", "compartment-service/target/compartment-service-1.0.0-SNAPSHOT-exec.jar"
Start-Process java -ArgumentList "-Dspring.profiles.active=mysql", "-jar", "optimization-service/target/optimization-service-1.0.0-SNAPSHOT-exec.jar"
Start-Process java -ArgumentList "-Dspring.profiles.active=mysql", "-jar", "validation-service/target/validation-service-1.0.0-SNAPSHOT-exec.jar"

# 5. Start API Gateway (Port 8080)
Start-Process java -ArgumentList "-jar", "api-gateway/target/api-gateway-1.0.0-SNAPSHOT-exec.jar"

# 6. Start React Frontend (Port 5173)
cd frontend
npm run dev -- --host
```

### Exact Shutdown Commands

```powershell
# 1. Stop all Java microservice processes
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force

# 2. Stop frontend Vite process
Get-Process node -ErrorAction SilentlyContinue | Stop-Process -Force

# 3. Stop Docker Compose containers
docker compose -f docker-compose.yml down
```

---

## 4. End-to-End Vertical Slice Workflow Evidence

The live vertical slice was executed against the API Gateway (`http://localhost:8080`) routing dynamically to real microservices and MySQL persistence:

```powershell
>>> 1. Authenticated as Planner. Token acquired.
>>> 2. Declaration Created in MySQL cargo_declaration_db: ID = e3076367-734d-49b4-ae93-f5ac376ced8a
>>> 3. Pinned Rule Snapshot in MySQL compatibility_rules_db: ID = snap-2026-q4-v1 (Rule: CMV-HAZ-1-3 requires 3000 mm segregation)
>>> 4. Assessment Created in MySQL compartment_db: ID = 29df21f1-8170-46fc-969c-327752cf7130, Status = DRAFT
>>> 5. Deterministic Optimization Produced: JobId = f5abf96a-add2-46f8-b9e6-a64bbd461656, Provenance = CONTROLLED_STUB, Status = FEASIBLE, Placements = 2
>>> 6. Candidate Layout Attached to Assessment: Status = CANDIDATE_READY, Digest = 472d7649967e16ceee38269f558fe9c47dc3a572e444a908639d8d05a2b57b98
>>> 7. Independent Validation Run: Passed = False, Findings Count = 1
>>> 8. Assessment Transitioned to: Status = VALIDATION_FAILED
>>> 9. Assessment Rejection Completed: Status = REJECTED
>>> 10. Downstream Declaration Workflow Projection: Status = REJECTED, Assessment ID = 29df21f1-8170-46fc-969c-327752cf7130
```

### Live Database State Verification (`SELECT ...` on MySQL 8.0)
```sql
SELECT id, reference_number, status FROM cargo_declaration_db.cargo_declarations;
-- Output: e3076367-734d-49b4-ae93-f5ac376ced8a | REF-2026-LIVE-FINAL | REJECTED

SELECT id, workflow_status, rule_snapshot_id FROM compartment_db.assessments;
-- Output: 29df21f1-8170-46fc-969c-327752cf7130 | REJECTED | snap-2026-q4-v1

SELECT event_id, event_type, status, aggregate_id FROM compartment_db.outbox_events;
-- Output: 327c2dcf-2493-43be-91da-63d4c0f4420b | CargoLoadRejected | PUBLISHED | 29df21f1-8170-46fc-969c-327752cf7130
```

### Live Kafka Message Consumed (`soa.s06.t08.events`)
```json
{
  "eventId": "327c2dcf-2493-43be-91da-63d4c0f4420b",
  "eventType": "CargoLoadRejected",
  "aggregateId": "29df21f1-8170-46fc-969c-327752cf7130",
  "aggregateType": "Assessment",
  "schemaVersion": "1.0.0",
  "occurredAt": "2026-10-27T09:05:16.661324200Z",
  "correlationId": "5beee6b9-c5ac-4fb5-9782-662da8b210c8",
  "payload": {
    "assessmentId": "29df21f1-8170-46fc-969c-327752cf7130",
    "declarationId": "e3076367-734d-49b4-ae93-f5ac376ced8a",
    "declarationVersion": 1,
    "layoutVersion": 1,
    "layoutDigest": "472d7649967e16ceee38269f558fe9c47dc3a572e444a908639d8d05a2b57b98",
    "ruleSnapshotId": "snap-2026-q4-v1",
    "rejectedBy": "usr-planner-001",
    "rejectionReasonCode": "HAZARD_SEGREGATION_VIOLATION",
    "rejectionNotes": "Separation distance of 2000mm between Class 1 Explosive and Class 3 Flammable Liquid is inadequate (requires 3000mm under CMV Rule 1-3)."
  }
}
```

---

## 5. Negative Test Cases Evidence

1. **Unauthorized Authentication**: Invalid credentials (`WrongPassword!`) was rejected with HTTP 401 Unauthorized (`INVALID_CREDENTIALS`).
2. **Premature Approval Block**: Attempting to approve an assessment in `DRAFT` state before zero-trust validation completed was blocked with HTTP 409 Conflict (`APPROVAL_BLOCKED: Assessment must be in PENDING_APPROVAL status`).
3. **Geometric Overlap Detection**: Submitting candidate layout with overlapping coordinates (`x=0, length=1200` vs `x=600, length=1200`) was evaluated by the independent 3D validator and failed (`passed: false`, finding: `GEOMETRIC_OVERLAP`).

---

## 6. Unit-Test vs. Live Integration Segregation Table

| Test Category | Execution Mechanism | Data Storage / Broker | Provenance / Stub | Verification Status |
| :--- | :--- | :--- | :--- | :--- |
| **Maven Unit Tests** (`mvn test`) | JUnit 5 + Spring Boot Test Slices | In-Memory H2 Database / Mocked Kafka | `CONTROLLED_STUB` | **PASS (3/3 passed in 1m 12s)** |
| **Microservice Startup** | Standalone Exec Jars (`java -jar`) | Live MySQL 8.0 on Port 3306 | Real MySQL Connection Pools | **PASS (All 6 service DBs updated)** |
| **Service Discovery** | Eureka Server (`:8761`) | In-Memory Registry with IP preference | Real Eureka Client Heartbeats | **PASS (All 7 instances UP)** |
| **Gateway Routing** | API Gateway (`:8080`) | Spring Cloud Gateway Netty Reactive | Dynamic Eureka LB resolution | **PASS (All `/api/v1/...` routes active)** |
| **Kafka Event Emission** | Compartment Service Outbox Poller | Apache Kafka (`localhost:9092`) | Real Kafka Producer (`acks=-1`) | **PASS (Topic `soa.s06.t08.events`)** |
| **Kafka Idempotent Consumer** | Cargo Declaration Consumer Group | Apache Kafka (`localhost:9092`) | Real Consumer Group sync | **PASS (Projection updated to `REJECTED`)** |
| **Frontend Live UI** | React 18 + Vite (`:5173`) | Live API Gateway (`localhost:8080`) | Dynamic fetch with JWT Auth Header | **PASS (Production build & dev server OK)** |
| **Optimizer Engine** | Controlled Stub Service | Optimization Database (`optimization_db`) | `CONTROLLED_STUB` | **PASS (Real CP-SAT deferred to M2)** |
