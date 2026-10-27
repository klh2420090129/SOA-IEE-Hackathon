# End-to-End Demo Runbook (Phase F.1)

## Overview

This runbook describes how to execute and verify the **SOA Team 8 Hazardous Cargo Co-Loading Platform** end-to-end demonstration.

The demonstration covers two deterministic operational scenarios:
1. **Scenario A (Valid Candidate Layout & Planner Approval)**: Authenticated declaration creation, compatibility assessment binding with pinned rule snapshot and digest verification, 3D heuristic optimization, candidate persistence with canonical digest match, independent zero-trust 3D validation engine execution, adversarial fail-closed verification on tampered digest, and role-based planner approval resulting in `APPROVED` assessment state.
2. **Scenario B (Rejected Workflow & Event Projection)**: Declaration creation, assessment initialization, load planner operational rejection, transactional outbox publication to Kafka topic `soa.s06.t08.events`, and downstream consumer projection update resulting in declaration state `REJECTED`.

---

## 1. Prerequisites and Infrastructure

### 1.1 Infrastructure Services (Docker)
Ensure Docker Desktop is running and start the core middleware:
```powershell
docker compose up -d
```

Verify healthy containers:
```powershell
docker ps
```
Required containers:
- `soa8-zookeeper`: Port 2181
- `soa8-kafka`: Port 9092 (Topics: `soa.s06.t08.events`, `soa.s06.t08.optimization.commands`, `soa.s06.t08.optimization.results`)
- `soa8-mysql`: Port 3306 (Databases: `auth_db`, `declaration_db`, `rules_db`, `compartment_db`, `optimization_db`, `validation_db`)

### 1.2 Microservice Startup Order
The microservices should be started in the following dependency order:

1. **Service Discovery (Eureka)**:
   ```powershell
   mvn -pl service-discovery spring-boot:run
   ```
   *Port: 8761* (`http://localhost:8761/`)

2. **API Gateway**:
   ```powershell
   mvn -pl api-gateway spring-boot:run
   ```
   *Port: 8080*

3. **Core Domain Services**:
   ```powershell
   # Auth Service (Port: 8081)
   mvn -pl auth-service spring-boot:run

   # Cargo Declaration Service (Port: 8082)
   mvn -pl cargo-declaration-service spring-boot:run

   # Compatibility Rule Service (Port: 8083)
   mvn -pl compatibility-rule-service spring-boot:run

   # Compartment Service (Port: 8084)
   mvn -pl compartment-service spring-boot:run

   # Optimization Service (Port: 8085)
   mvn -pl optimization-service spring-boot:run

   # Validation Service (Port: 8086)
   mvn -pl validation-service spring-boot:run
   ```

4. **Python Optimizer Worker**:
   ```powershell
   python workers/optimizer-worker/worker.py
   ```

---

## 2. Authentication & Environment Configuration

Default test accounts are seeded in `auth-service`:
- **Shipper**: `shipper@soa8.org` / `ShipperPass123!` (Role: `ROLE_SHIPPER`)
- **Load Planner**: `planner@soa8.org` / `PlannerPass123!` (Role: `ROLE_LOAD_PLANNER`)

Credentials can optionally be overridden via environment variables without committing secrets:
```powershell
$env:SOA8_SHIPPER_USER = "shipper@soa8.org"
$env:SOA8_SHIPPER_PASS = "ShipperPass123!"
$env:SOA8_PLANNER_USER = "planner@soa8.org"
$env:SOA8_PLANNER_PASS = "PlannerPass123!"
$env:GATEWAY_URL = "http://localhost:8080"
```

---

## 3. Running the Automated Demo

Run the automated, fail-closed PowerShell script:
```powershell
powershell.exe -ExecutionPolicy Bypass -File .\scripts\run-demo.ps1
```

### Execution Options
- `-GatewayBaseUrl <url>`: Override API Gateway endpoint (default: `http://localhost:8080`).
- `-EvidenceDir <path>`: Custom output folder for artifacts and evidence logs.
- `-SkipReadinessCheck`: Bypass the Eureka/Gateway connectivity pre-check.

---

## 4. Expected State Transitions & Evidence Artifacts

The demo run writes structured JSON outputs and execution logs into `evidence/demo-<timestamp>/`:

| Step | Action | Expected Output | Evidence File |
|---|---|---|---|
| **0** | User Login | JWT tokens issued for Shipper & Planner | `auth-shipper.json`, `auth-planner.json` (Tokens redacted) |
| **0.1** | Pinned Rule Snapshot | Rule Snapshot `snap-2026-q4-v1` verified with SHA-256 digest | `rule-snapshot.json` |
| **A.1** | Submit Declaration | Status `SUBMITTED`, Version `1` | `scenario-a-01-declaration.json` |
| **A.2** | Draft Assessment | Status `DRAFT`, bound to `snap-2026-q4-v1` | `scenario-a-02-assessment-draft.json` |
| **A.3** | 3D Optimization Dispatch | `solverStatus=FEASIBLE`, `provenance=HEURISTIC_EP_3D_V1` | `scenario-a-03-layout-result.json`, `scenario-a-03-opt-job-response.json` |
| **A.4** | Candidate Recording | Status `CANDIDATE_READY`, digest matches optimizer | `scenario-a-04-candidate-recorded.json` |
| **A.5** | Zero-Trust 3D Validation | Status `passed=true`, validatorVersion `1.0.0-m1` | `scenario-a-05-validation-result.json` |
| **A.6** | Bind Validation Outcome | Assessment transitions to `PENDING_APPROVAL` | `scenario-a-06-assessment-pending-approval.json` |
| **A.7** | Adversarial Tamper Check | Attempt to approve with corrupted digest rejects with `HTTP 409` | Logged in `demo-execution.log` |
| **A.8** | Planner Approval | Status `APPROVED`, decision `APPROVED` | `scenario-a-08-assessment-approved.json`, `scenario-a-09-final-assessment.json` |
| **B.1** | Submit Declaration B | Status `SUBMITTED` | `scenario-b-01-declaration.json` |
| **B.2** | Draft Assessment B | Status `DRAFT` | `scenario-b-02-assessment-draft.json` |
| **B.3** | Planner Rejection | Status `REJECTED`, reason `OPERATIONAL_CAPACITY_HOLD` | `scenario-b-03-assessment-rejected.json` |
| **B.4** | Kafka Outbox & Projection | Kafka event published; downstream declaration updates to `REJECTED` | `scenario-b-04-declaration-projected.json` |

---

## 5. Safety, Correctness, and Zero-Trust Guarantees

1. **Pinned Snapshot Immutability**: All assessments lock `ruleSnapshotId` and its SHA-256 `snapshotDigest`. Validation and approval fail if an altered rule snapshot is supplied.
2. **Deterministic Canonical Digest**: Placements are normalized and hashed using SHA-256 (`CandidateDigestCalculator`). Both Java and Python workers follow the exact canonical schema.
3. **Fail-Closed Planner Approval**:
   - Only users with `ROLE_LOAD_PLANNER` are authorized.
   - If `expectedDigest` provided by planner does not byte-for-byte match the candidate content digest, Compartment service throws HTTP 409 Conflict.
4. **Authoritative Validation Provenance**: The validation record is created and signed exclusively by `validation-service`. Compartment verifies the validation ID exists and binds to the exact layout version before moving to `PENDING_APPROVAL`.
5. **Idempotent Kafka Event Consumption**: The `cargo-declaration-service` tracks consumed event IDs in `consumed_events` table, preventing double-processing on Kafka replay.

---

## 6. Troubleshooting

- **Eureka Unreachable (`http://localhost:8761`)**:
  Check `mvn -pl service-discovery spring-boot:run` log. Ensure port 8761 is not bound by a zombie Java process (`Get-Process -Id (Get-NetTCPConnection -LocalPort 8761).OwningProcess`).
- **HTTP 404 from API Gateway**:
  Remember all routes through the Gateway require the `/api/v1/` prefix (e.g. `/api/v1/declarations`, `/api/v1/assessments`).
- **Optimization Worker Timeout**:
  Verify Kafka container is running (`docker ps`) and `python workers/optimizer-worker/worker.py` is listening on topic `soa.s06.t08.optimization.commands`.
- **Downstream Declaration Does Not Update to `REJECTED`**:
  Check `compartment-service` logs for transactional outbox scheduled publisher, and `cargo-declaration-service` logs for `CargoEventListener`.
