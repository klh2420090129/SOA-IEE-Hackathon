# Phase G.11A — Append-Only Decision Audit Trail and Approval Eventing Implementation Report

**Platform:** SOA Team 8 Hazardous Cargo Freight Planning Platform
**Target Repository:** `klh2420090129/SOA-IEE-Hackathon`
**Branch:** `feat/milestone-1-vertical-slice`
**Base Commit:** `61af6be`
**Date:** October 28, 2026
**Implementation Engineer:** Antigravity IDE Autonomous Agentic Engineering System

---

## 1. Executive Summary & Objective

In **Phase G.11**, the operational gap analysis revealed that while candidate rejections persisted a `CargoLoadRejected` event to the transactional outbox, candidate approvals only updated the mutable `AssessmentEntity` in-place without generating a corresponding domain event or preserving an immutable historical decision ledger.

In **Phase G.11A**, the following capabilities were engineered and verified:
1. **Append-Only Decision Audit Trail:** Implemented an immutable audit ledger (`assessment_decision_audit`) in `compartment-service` capturing every successful approval and rejection transition within the exact transactional boundary.
2. **Strict Actor Identity Claims Enforcement:** Planners cannot spoof identity via request body fields (`req.plannerId`). The authenticated actor identity is strictly derived from the validated JWT token claims (`claims.getSubject()`).
3. **Approval Domain Eventing (`CargoLoadApproved`):** Added `CargoLoadApproved` event to the transactional outbox with byte-for-byte schema consistency with `CargoLoadRejected`.
4. **Idempotent Downstream Consumer:** Implemented consumer handling for `CargoLoadApproved` in `cargo-declaration-service` updating the downstream read projection to `APPROVED` with deduplication via `consumed_events`.
5. **REST Audit Trail Endpoint:** Exposed `GET /api/v1/assessments/{id}/audit-trail` returning the chronological decision history.

---

## 2. Files Modified & Added

### 2.1 Contracts & Event Schemas
* **Added:** [`contracts/events/cargo-load-approved.schema.json`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/contracts/events/cargo-load-approved.schema.json) — JSON Schema draft-07 for `CargoLoadApprovedEvent`.
* **Added:** [`common-contracts/.../events/CargoLoadApprovedPayload.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/common-contracts/src/main/java/org/soa8/freight/common/events/CargoLoadApprovedPayload.java) — Java DTO matching JSON schema.
* **Modified:** [`common-contracts/.../constants/EventTopics.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/common-contracts/src/main/java/org/soa8/freight/common/constants/EventTopics.java) — Added `EVENT_TYPE_CARGO_LOAD_APPROVED = "CargoLoadApproved"`.

### 2.2 Compartment Service (Assessment Owner)
* **Added:** [`compartment-service/.../model/AssessmentDecisionAuditEntity.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/compartment-service/src/main/java/org/soa8/freight/compartment/model/AssessmentDecisionAuditEntity.java) — JPA entity mapping to `assessment_decision_audit` table.
* **Added:** [`compartment-service/.../repository/AssessmentDecisionAuditRepository.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/compartment-service/src/main/java/org/soa8/freight/compartment/repository/AssessmentDecisionAuditRepository.java) — Query methods by `assessmentId` and `declarationId`.
* **Modified:** [`compartment-service/.../service/AssessmentWorkflowService.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/compartment-service/src/main/java/org/soa8/freight/compartment/service/AssessmentWorkflowService.java):
  - In `approveCandidate(...)`: writes audit record to `decisionAuditRepository` and schedules `CargoLoadApproved` in `outboxEventRepository`.
  - In `rejectCandidate(...)`: writes audit record to `decisionAuditRepository` alongside existing `CargoLoadRejected` event.
  - Fail-closed guarantees: failed validations, stale versions, or unauthorized attempts rollback transactionally with zero audit records or events emitted.
* **Modified:** [`compartment-service/.../controller/CompartmentController.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/compartment-service/src/main/java/org/soa8/freight/compartment/controller/CompartmentController.java):
  - Enforced `claims.getSubject()` for planner identity (ignoring spoofed `req.plannerId`).
  - Added `GET /api/v1/assessments/{id}/audit-trail`.

### 2.3 Cargo Declaration Service (Downstream Consumer)
* **Modified:** [`cargo-declaration-service/.../kafka/CargoEventListener.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/cargo-declaration-service/src/main/java/org/soa8/freight/declaration/kafka/CargoEventListener.java) — Added deserialization and routing for `CargoLoadApproved`.
* **Modified:** [`cargo-declaration-service/.../service/DeclarationWorkflowService.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/cargo-declaration-service/src/main/java/org/soa8/freight/declaration/service/DeclarationWorkflowService.java) — Added `processCargoLoadApproved` with deduplication check against `consumed_events` table and update to `APPROVED`.

### 2.4 Tests
* **Modified:** [`compartment-service/.../CompartmentAssessmentWorkflowTest.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/compartment-service/src/test/java/org/soa8/freight/compartment/CompartmentAssessmentWorkflowTest.java) — Added 3 unit tests verifying audit record creation on approval, rejection, and ensuring failed approvals do not create audit records.
* **Modified:** [`compartment-service/.../CompartmentControllerSecurityTest.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/compartment-service/src/test/java/org/soa8/freight/compartment/CompartmentControllerSecurityTest.java) — Verified that spoofed `req.plannerId` is ignored in favor of `claims.getSubject()`, and tested the `/audit-trail` endpoint.
* **Modified:** [`compartment-service/.../EndToEndVerticalSliceIntegrationTest.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/compartment-service/src/test/java/org/soa8/freight/integration/EndToEndVerticalSliceIntegrationTest.java) — Verified complete end-to-end audit trail write, Kafka domain event generation for `CargoLoadRejected` and `CargoLoadApproved`, and idempotent consumer projection updates.

---

## 3. Database Migration Instructions

Because Spring Data JPA uses `spring.jpa.hibernate.ddl-auto: update`, the new `assessment_decision_audit` table is automatically created by Hibernate during application startup.

For environments requiring explicit DDL migration (e.g. production MySQL Flyway/Liquibase):
```sql
CREATE TABLE IF NOT EXISTS assessment_decision_audit (
    audit_id VARCHAR(36) PRIMARY KEY,
    assessment_id VARCHAR(36) NOT NULL,
    declaration_id VARCHAR(36) NOT NULL,
    declaration_version INT,
    layout_version INT,
    layout_digest VARCHAR(64),
    rule_snapshot_id VARCHAR(64),
    validation_id VARCHAR(36),
    decision VARCHAR(20) NOT NULL,
    reason_code VARCHAR(100),
    notes VARCHAR(2000),
    decided_by VARCHAR(100) NOT NULL,
    decided_at TIMESTAMP(6) NOT NULL,
    correlation_id VARCHAR(36),
    INDEX idx_audit_assessment (assessment_id),
    INDEX idx_audit_declaration (declaration_id)
);
```

---

## 4. Test Verification Evidence

All tests were executed locally and passed with zero errors:

| Suite / Test Run | Command | Result | Notes |
| :--- | :--- | :---: | :--- |
| **Compartment Service Tests** | `mvn test -pl compartment-service` | **BUILD SUCCESS** | 19 tests run, 0 failures, 0 errors. |
| **Clean Multi-Module Test** | `mvn clean test -pl common-contracts,cargo-declaration-service,compartment-service` | **BUILD SUCCESS** | 19 tests run, 0 failures, 0 errors. |
| **Full Maven Reactor Clean Test** | `mvn clean test` | **BUILD SUCCESS** | 10 / 10 modules compiled and tested cleanly (2m 28s). |
| **Frontend Vitest Suite** | `cd frontend && npm test -- --run` | **PASS** | 10 test files passed, 48 / 48 tests passed (100%). |
| **Frontend Production Build** | `cd frontend && npm run build` | **PASS** | Built bundle in 7.98s with 0 errors. |
| **Python Optimizer Pytest** | `pytest workers/optimizer-worker` | **PASS** | 31 / 31 passed in 2.40s (100%). |

---

## 5. Git Status

All changes are cleanly tracked in the working directory on branch `feat/milestone-1-vertical-slice`. No unrelated files or directories were modified, and the prior G.10 TokenValidator disambiguation changes remain safely preserved. Per constraints, no commit or push was performed.
