# Phase G.4 — End-to-End Integration, Browser Verification & Demo Hardening Audit

**Repository:** `https://github.com/klh2420090129/SOA-IEE-Hackathon`  
**Target Branch:** `feat/milestone-1-vertical-slice`  
**Starting / Audited Commit:** `ab7de18` (`feat(frontend): implement interactive 3D cargo loading simulator with React Three Fiber, camera presets, and coordinate integrity`)  
**Audit Timestamp:** October 28, 2026  
**Auditor:** SOA Team 8 Senior Full-Stack & Integration Engineer  

---

## 1. Actual Repository State & Baseline Commit

The repository state was inspected prior to test and integration runs:

- **Current Git Branch:** `feat/milestone-1-vertical-slice` (up to date with `origin/feat/milestone-1-vertical-slice`).
- **HEAD Commit:** `ab7de183713f0cf748d5d4d3a060136208cb187a` (`ab7de18`).
- **Working Tree Status:** Clean working directory across all tracked source files. Untracked artifacts are confined to timestamped demo evidence runs under `evidence/demo-*`.
- **Preceding Phase Reports Confirmed:**
  - `docs/implementation/phase-g1-reconnaissance.md` (Commit `35bf3ca`)
  - `docs/implementation/phase-g2-frontend-modularization.md` (Commit `1d42cbe`)
  - Phase G.3 Interactive 3D Simulator (Commit `ab7de18`)
- **Frontend Changes Status:** All modular API clients, React router contexts, 2D visualizer, and 3D React Three Fiber visualizers are committed and present in the branch.

---

## 2. Architecture & API Contract Verification

Trace of frontend requests through the Spring Cloud API Gateway (port 8080) to backend microservices:

| Action / Flow | Frontend Route / Client Method | Backend Controller Route | Target Service | Status Code / Contract |
|---|---|---|---|---|
| User Login | `authApi.login()` | `POST /api/v1/auth/login` | `auth-service` (8081) | `200 OK` + JWT Bearer token |
| Session Health | `authApi.checkHealth()` | `GET /api/v1/auth/health` | `auth-service` (8081) | `200 OK` |
| Shipper Declaration Creation | `declarationApi.create()` | `POST /api/v1/declarations` | `cargo-declaration-service` (8082) | `201 CREATED` |
| Shipper Declaration Fetch | `declarationApi.getById()` | `GET /api/v1/declarations/{id}` | `cargo-declaration-service` (8082) | `200 OK` |
| Pinned Rule Snapshot Retrieval | `rulesApi.getSnapshot()` | `GET /api/v1/rule-snapshots/{id}` | `compatibility-rule-service` (8083) | `200 OK` (with SHA-256 digest) |
| Compatibility Assessment Init | `assessmentApi.create()` | `POST /api/v1/assessments` | `compartment-service` (8084) | `202 ACCEPTED` (Status: `DRAFT`) |
| Assessment Retrieval | `assessmentApi.getById()` | `GET /api/v1/assessments/{id}` | `compartment-service` (8084) | `200 OK` |
| Optimization Job Submission | `optimizationApi.submit()` | `POST /api/v1/optimization-jobs` | `optimization-service` (8085) | `202 ACCEPTED` (Status: `SUBMITTED`) |
| Optimization Polling | `optimizationApi.getById()` | `GET /api/v1/optimization-jobs/{id}` | `optimization-service` (8085) | `200 OK` (`SUBMITTED` -> `COMPLETED`) |
| Candidate Placement Record | `assessmentApi.recordCandidate()` | `POST /api/v1/assessments/{id}/candidate` | `compartment-service` (8084) | `200 OK` (Status: `CANDIDATE_READY`) |
| Independent 3D Validation | `validationApi.validate()` | `POST /api/v1/validations` | `validation-service` (8086) | `200 OK` (`passed: true/false`) |
| Authoritative Validation Binding | `assessmentApi.recordValidation()` | `POST /api/v1/assessments/{id}/validation` | `compartment-service` (8084) | `200 OK` (Status: `PENDING_APPROVAL`) |
| Planner Approval | `assessmentApi.approve()` | `POST /api/v1/assessments/{id}/approve` | `compartment-service` (8084) | `200 OK` (`APPROVED`) / `409 CONFLICT` |
| Planner Rejection | `assessmentApi.reject()` | `POST /api/v1/assessments/{id}/reject` | `compartment-service` (8084) | `200 OK` (`REJECTED` + Kafka outbox event) |

### Role-Based Access Control (RBAC) Verification
A security authorization audit was conducted against the running gateway endpoints:
1. **Unauthenticated Access (`401 Unauthorized`):** Calling `POST /api/v1/assessments/{id}/approve` without a Bearer token returns `HTTP 401 Unauthorized`.
2. **Wrong-Role Access (`403 Forbidden`):** Calling `POST /api/v1/assessments/{id}/approve` using a valid `ROLE_SHIPPER` token returns `HTTP 403 Forbidden`. Frontend controls being disabled are backed by backend authorization enforcement.
3. **Authorized Load Planner Access (`200 OK` / `404 Not Found` for non-existent assessment):** Successfully passes security gate with `ROLE_LOAD_PLANNER`.

---

## 3. Exact Commands & Automated Test Results

### 3.1 Frontend Test & Production Build
```powershell
cd frontend
npm test -- --run
npm run build
```
- **Vitest Unit & Simulation Tests:** `5 test files passed (5)`, `20 tests passed (20)`, 0 failed.
  - `coordinateUtils.test.ts`: 7 tests passed (millimeter-to-world conversion, 90° yaw dimension swapping, bounding coordinates, hazard colors).
  - `CandidateViewer3D.test.tsx`: 4 tests passed (empty state handling, R3F canvas mounting, camera view buttons, sequential loading controls).
  - `CandidateViewer2D.test.tsx`: 4 tests passed (SVG top-down and elevation views, table fallback, violation highlighting).
  - `useOptimizationJob.test.ts`: 3 tests passed (job submission, status polling, terminal error propagation).
  - `App.test.tsx`: 2 tests passed (unauthenticated login landing, navigation bar branding).
- **TypeScript & Vite Production Build:** Exit code 0, 2086 modules transformed without errors:
  - `dist/index.html` (0.72 kB)
  - `dist/assets/index-CyCFI9Ex.css` (2.11 kB)
  - `dist/assets/troika-three-text.esm-B1e0OvWV.js` (118.74 kB)
  - `dist/assets/index-DwVolqUF.js` (1,121.54 kB)

### 3.2 Java Microservice Test Suite
```powershell
mvn test
```
- **Execution Summary:** 10 reactor projects built and tested.
- **Total Tests Run:** 16 tests across all services, 0 failures, 0 errors, 0 skipped.
  - `IndependentValidatorTests` (`validation-service`): 10 tests passed (bounding box containment, 3D cuboid collision, hazard distance segregation, fail-closed unstackable loads, tampered digest rejection).
  - `EndToEndVerticalSliceIntegrationTest` (`compartment-service`): 2 tests passed (Scenario A approval workflow, Scenario B rejection & idempotent event deduplication).
  - `AuthServiceApplicationTests` (`auth-service`): 1 test passed.
  - `CargoDeclarationApplicationTests` (`cargo-declaration-service`): 1 test passed.
  - `CompatibilityRuleApplicationTests` (`compatibility-rule-service`): 1 test passed.
  - `OptimizationApplicationTests` (`optimization-service`): 1 test passed.

### 3.3 Python Optimizer Worker Test Suite
```powershell
pytest workers/optimizer-worker/ -v
```
- **Test Session:** Python 3.12.10, pytest-9.1.1.
- **Results:** 31 tests passed in 2.88 seconds:
  - `test_benchmark_suite.py`: 9 passed (infeasibility proof separation, primary vs lexicographic proof rates, duplicate solver inputs).
  - `test_cpsat_solver.py`: 13 passed (OR-Tools CP-SAT single/multi-item optimization, diagonal Euclidean hazard segregation, 90° rotation, fail-closed unstackable items).
  - `test_dedup_store.py`: 3 passed (SQLite deduplication store lifecycle, crash recovery, duplicate command rejection).
  - `test_heuristic_solver.py`: 6 passed (deterministic tie-breaking, fail-closed unknown stacking, hazard segregation distance, cross-language digest parity).

---

## 4. Live Integrated Execution Results

Services were started in the documented order:
1. Docker middleware (`soa8-mysql`, `soa8-kafka`, `soa8-zookeeper`).
2. Eureka Service Discovery (`service-discovery` on port 8761).
3. API Gateway (`api-gateway` on port 8080).
4. Microservices (`auth-service`: 8081, `cargo-declaration-service`: 8082, `compatibility-rule-service`: 8083, `compartment-service`: 8084, `optimization-service`: 8085, `validation-service`: 8086).
5. Python CP-SAT Worker (`workers/optimizer-worker/worker.py` listening on Kafka consumer group `optimizer-worker-python-group`).
6. Vite Frontend (`localhost:5173`).

All 7 Spring Boot applications registered dynamically with Eureka:
```
OPTIMIZATION-SERVICE               1
API-GATEWAY                        1
COMPATIBILITY-RULE-SERVICE         1
AUTH-SERVICE                       1
VALIDATION-SERVICE                 1
COMPARTMENT-SERVICE                1
CARGO-DECLARATION-SERVICE          1
```

### Scenario A — Successful Planner Approval
Executed via `scripts/run-demo.ps1` through the API Gateway:
- **Shipper Authentication:** User ID `usr-shipper-001`, role `ROLE_SHIPPER`.
- **Planner Authentication:** User ID `usr-planner-001`, role `ROLE_LOAD_PLANNER`.
- **Pinned Rule Snapshot:** Snapshot `snap-2026-q4-v1` (`INDIA_CMV_RULES`, SHA-256 `5ee118f2a2f61bc8112e51d86e69aaa4080d1b7cffc5de98ea866e905c95f782`).
- **Declaration Created:** ID `f16a81ee-b9df-41c1-bfa4-43fbcc5d07c0`, status `SUBMITTED`.
- **Assessment Created:** ID `bbe03305-6537-46a1-ba8f-c3a288a8a6d5`, status `DRAFT`.
- **Optimization Job:** ID `07e33502-8e95-4346-bccd-f9f8187bce5e`. Candidate layout computed with 2 placements, digest `9e0f0bbc8e741f8c74a85c380979c2f888402f463295f0f18d38961b950f2e38`.
- **Candidate Recorded:** Assessment transitioned to `CANDIDATE_READY`.
- **Independent Validation:** Engine run `19f1dd0a-71a8-448a-9f22-60fb86933059` returned `passed: true`.
- **Validation Bound:** Assessment transitioned to `PENDING_APPROVAL`.
- **Planner Approval:** Successfully submitted and committed. Final workflow state: `APPROVED`.

### Scenario B — Planner Rejection & Kafka Event Outbox
- **Declaration Created:** ID `9fc08e17-0709-42d3-8a01-73b270a5b11c`.
- **Assessment Created:** ID `179077d4-69dd-432e-aeb5-a152b10c57b8`.
- **Planner Decision:** Rejected with explicit reason code `OPERATIONAL_CAPACITY_HOLD`.
- **Transactional Outbox & Kafka Event:** Assessment updated to `REJECTED`, writing transactional outbox record to `soa.s06.t08.events`.
- **Downstream Projection:** `cargo-declaration-service` consumed event and updated Declaration `9fc08e17-0709-42d3-8a01-73b270a5b11c` projection status to `REJECTED`.
- **Deduplication:** Duplicate delivery assertion confirmed that redelivery of the rejection event does not duplicate state mutations.

### Scenario C — Fail-Closed Validation & Tampered Digest Assertion
- In both `scripts/run-demo.ps1` and `scripts/verify-cpsat-and-compare.ps1`, an adversarial approval payload was submitted with an intentionally corrupted digest (`tampered-digest-xyz` instead of the canonical layout SHA-256).
- **Backend Response:** Rejected immediately with `HTTP 409 Conflict` (`APPROVAL_BLOCKED: Candidate layout digest does not match current assessment candidate`).
- Approval was completely blocked, preserving cryptographic integrity.

### Python CP-SAT Solver End-to-End Verification
Executed via `scripts/verify-cpsat-and-compare.ps1`:
- Optimization job `1d5a7d50-6a92-4768-9e54-903b96433dc5` requested algorithm `PYTHON_CPSAT_3D_V1`.
- Python worker consumed the Kafka command, solved the lexicographic 3D packing problem using OR-Tools CP-SAT (version 9.10.4067), proved full lexicographic optimality in 4.2ms (251 branches), and emitted candidate digest `33c394a1ee09d28cd7f6b1ebb84ddc5672e463bd7063c1d7abc17d305e582df9`.
- Optimization service received the result via Kafka topic `soa.s06.t08.optimization.results` and stored provenance `PYTHON_ORTOOLS_CPSAT_3D_V1`.
- Assessment `92efe6da-f3c4-4f01-bf71-3b8fb6a63f4a` was independently validated, verified, and approved.

---

## 5. Geometry & 3D Safety Semantics Audit

Inspection of `frontend/src/components/simulation/coordinateUtils.ts` and `CandidateViewer3D.tsx` against backend DTOs:

1. **Origin & Axis Alignment:**
   - **Backend:** Compartment origin $(0, 0, 0)$ is at floor level, front-left corner.
     - $X \in [0, \text{lengthMm}]$ (longitudinal axis, cab to rear doors).
     - $Y \in [0, \text{widthMm}]$ (lateral floor width).
     - $Z \in [0, \text{heightMm}]$ (vertical elevation above trailer floor).
   - **Three.js Scene Coordinate Mapping:**
     - Three $X = (x_{\text{mm}} \times 0.001) - (\text{length}_{\text{m}} / 2) + (\text{placedLength}_{\text{m}} / 2)$
     - Three $Y = (z_{\text{mm}} \times 0.001) + (\text{placedHeight}_{\text{m}} / 2)$ (floor fixed at $Y=0$, boxes sit on top of floor)
     - Three $Z = (y_{\text{mm}} \times 0.001) - (\text{width}_{\text{m}} / 2) + (\text{placedWidth}_{\text{m}} / 2)$
   - Result: Exactly mirrors physical space without coordinate inversion.

2. **Rotation Semantics:**
   - When orientation is `ROTATE_YAW_90`, the solver outputs length and width dimensions swapped in the `ItemPlacementDto`. The frontend calculates bounding box extents directly from placement `lengthMm` and `widthMm`.

3. **Boundary Violations & Integrity:**
   - Placements outside compartment bounds $[0, \text{compL}]$, $[0, \text{compW}]$, or $[0, \text{compH}]$ physically render protruding through the wireframe cage.
   - Any cargo item with validation findings is flagged with `isViolation=true`, highlighting the mesh in bright crimson red (`#ef4444`) with high-contrast alert edges (`#fee2e2`).

4. **Safety Semantics & Audit Notice:**
   - The 3D simulator **never** independently declares a layout safe.
   - A persistent Cryptographic Safety Notice is rendered directly beneath the viewport:
     > *"3D geometry rendering is for visual inspection and spatial comprehension only. Authoritative co-loading safety is established exclusively by the independent Zero-Trust Validation Engine and Load Planner sign-off against statutory CMV/IMDG rule snapshot."*
   - Status indicators explicitly state solver status (`OPTIMAL`/`FEASIBLE`) and validator status (`VERIFIED PASSED` / `VIOLATION DETECTED`), preventing users from confusing mathematical optimization with statutory compliance.

---

## 6. Environmental Blockers & Verification Distinctions

In strict compliance with Phase G.4 completion criteria, the following distinction is reported:

1. **Automated Unit & Simulation Tests:** **PASSED (100%)**
   - 20 frontend Vitest tests, 16 backend Java JUnit tests, and 31 Python pytest tests.
2. **Integrated Service Pipeline:** **PASSED (100%)**
   - All 7 Spring Boot services, Eureka, MySQL, Kafka, and Python worker communicated over HTTP, REST, and Kafka. Scenarios A, B, and C passed end-to-end.
3. **Interactive Headless Browser Verification (Playwright Subagent):** **BLOCKED BY ENVIRONMENT**
   - When `browser_subagent` invoked `open_browser_url`, Playwright failed to start because the offline execution environment has no external internet access to fetch the Playwright browser binaries (`playwright-1.57.0-win32_x64.zip` returned 404/network unreachable).
   - **Alternative Verification:** The frontend dev server was confirmed serving HTTP 200 on `http://localhost:5173`. Component mounting and simulation canvas interactions were verified via Vitest DOM tests.

---

## 7. Startup & Reproduction Instructions

To reproduce the verified end-to-end demo:

1. **Start Middleware (Docker):**
   ```powershell
   docker compose up -d
   ```
2. **Start Microservices (in order or background daemons):**
   ```powershell
   mvn -pl service-discovery spring-boot:run
   mvn -pl api-gateway spring-boot:run
   mvn -pl auth-service spring-boot:run
   mvn -pl cargo-declaration-service spring-boot:run
   mvn -pl compatibility-rule-service spring-boot:run
   mvn -pl compartment-service spring-boot:run
   mvn -pl optimization-service spring-boot:run
   mvn -pl validation-service spring-boot:run
   ```
3. **Start Python Optimization Worker:**
   ```powershell
   python workers/optimizer-worker/worker.py
   ```
4. **Execute End-to-End Scenarios:**
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File .\scripts\run-demo.ps1
   powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-cpsat-and-compare.ps1
   ```
5. **Start Frontend:**
   ```powershell
   cd frontend
   npm run dev
   ```
   Open `http://localhost:5173` in a web browser. Log in as `shipper@soa8.org` / `ShipperPass123!` or `planner@soa8.org` / `PlannerPass123!`.
