# Phase G.1 — Frontend Reconnaissance & Foundation Report
**Project:** Hazardous Cargo Co-Loading and 3D Load Planning System (SOA Team 8)  
**Repository:** `https://github.com/klh2420090129/SOA-IEE-Hackathon`  
**Working Branch:** `feat/milestone-1-vertical-slice`  
**Baseline Git Commit:** `7be8d52` (Phase F.4.2 Verified & Pushed)  
**Date:** 2026-10-28  

---

## 1. Executive Summary & Objective

Phase G.1 performs rigorous frontend reconnaissance to establish an evidence-based foundation for building the React + TypeScript frontend. The goal is to support the complete Shipper and Load Planner operational workflows—including declaration authoring, rule compatibility inspection, optimization job tracking, independent zero-trust validation audit, authoritative load planner approval/rejection, and interactive 3D compartment digital twin simulation.

No backend architectures or contracts were modified or fabricated. Every inspected endpoint, DTO field, status enum, role constraint, and data structure was verified directly from the active Spring Boot microservices controllers and Java contracts.

---

## 2. Git & Repository Status Inspection

- **Current Branch:** `feat/milestone-1-vertical-slice` (tracking `origin/feat/milestone-1-vertical-slice`)
- **HEAD Commit:** `7be8d52` — `fix(worker): align supported algorithm identifiers across solver modules`
- **Descendant of Phase F.4.2:** Yes, `7be8d52` directly follows commit `7bfd366` (Phase F.4.1 reconciliation) and verified the live F.1 and F.2 demo test suites.
- **Working Tree Cleanliness:** Clean; untracked evidence directories from live runs (`evidence/demo-20261028-093110`, etc.) preserved intact.
- **Existing Frontend Directory:** Located at `frontend/`. Contains:
  - `package.json`: Configured with React `18.2.0`, React DOM `18.2.0`, Lucide React `0.344.0`, TypeScript `5.2.2`, Vite `5.1.6`.
  - `vite.config.ts`: Defines dev server port `5173` with reverse proxy `/api` targeting the API Gateway at `http://localhost:8080`.
  - `src/App.tsx`: A single 1,205-line proof-of-concept component with manual workflow execution, basic SVG 2D layout rendering, and approval/rejection modals.
  - Build verification: `npm run build` executed successfully (`tsc && vite build`, 1470 modules transformed, 0 errors).

---

## 3. Verified Backend API Contract Inventory

All microservices are reachable through the Spring Cloud API Gateway at `http://localhost:8080` (port 8080).

### 3.1 Authentication & Role Identification (`auth-service`)
- **Controller Source:** [`AuthController.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/auth-service/src/main/java/org/soa8/freight/auth/controller/AuthController.java)
- **Gateway Route:** `/api/v1/auth/**` -> `lb://auth-service`

| Endpoint | Method | Auth / Role | Request Schema | Response Schema & Codes | State Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/v1/auth/login` | `POST` | Public | `{ username, password }` | **200 OK**: `{ token, tokenType: "Bearer", expiresInSeconds: 3600, userId, username, roles: string[] }`<br>**401 Unauthorized**: `{ code: "INVALID_CREDENTIALS", message }` | Seed credentials:<br>- Shipper: `shipper@soa8.org` / `ShipperPass123!` (`ROLE_SHIPPER`)<br>- Planner: `planner@soa8.org` / `PlannerPass123!` (`ROLE_LOAD_PLANNER`) |
| `/api/v1/auth/me` | `GET` | Bearer Token | Headers: `Authorization: Bearer <token>` | **200 OK**: `{ userId, username, roles: string[] }`<br>**401 Unauthorized**: `{ code: "INVALID_TOKEN" \| "UNAUTHORIZED", message }` | Token claims extraction & expiration check |

### 3.2 Cargo Declarations & Projections (`cargo-declaration-service`)
- **Controller Source:** [`CargoDeclarationController.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/cargo-declaration-service/src/main/java/org/soa8/freight/declaration/controller/CargoDeclarationController.java)
- **Gateway Route:** `/api/v1/declarations/**` -> `lb://cargo-declaration-service`

| Endpoint | Method | Auth / Role | Request Schema | Response Schema & Codes | State Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/v1/declarations` | `POST` | Shipper (`ROLE_SHIPPER`) | `{ referenceNumber, shipperId, origin, destination, items: [{ cargoItemId, description, hazardClass, unNumber, lengthMm, widthMm, heightMm, weightKg, stackable, maxStackWeightKg, quantity }] }` | **201 Created**: Complete `CargoDeclaration` entity with generated `id`, `version: 1`, `items` list.<br>**400 Bad Request**: `{ code: "VALIDATION_ERROR", message }` | Creates initial declaration; items assigned unique UUIDs if not passed. |
| `/api/v1/declarations/{id}` | `GET` | Authenticated | None | **200 OK**: `CargoDeclaration`<br>**404 Not Found**: `{ code: "DECLARATION_NOT_FOUND" }` | Authoritative declaration retrieval |
| `/api/v1/declarations/{id}/workflow` | `GET` | Authenticated | None | **200 OK**: `DeclarationWorkflowProjection` (`{ declarationId, lastAssessmentId, status, lastRejectionReasonCode, updatedAt }`)<br>**404 Not Found**: If declaration does not exist | Downstream projection updated automatically via Kafka events (`CargoLoadRejected` -> `REJECTED`). Fallbacks to `SUBMITTED`. |

### 3.3 Safety Rules & Governance Snapshots (`compatibility-rule-service`)
- **Controller Source:** [`CompatibilityRuleController.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/compatibility-rule-service/src/main/java/org/soa8/freight/rules/controller/CompatibilityRuleController.java)
- **Gateway Route:** `/api/v1/rules/**`, `/api/v1/rule-snapshots/**` -> `lb://compatibility-rule-service`

| Endpoint | Method | Auth / Role | Request Schema | Response Schema & Codes | State Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/v1/rules` | `GET` | Authenticated | None | **200 OK**: `List<CompatibilityRuleDto>` (`[{ ruleId, ruleCode, primaryHazardClass, secondaryHazardClass, segregationType, minDistanceMm, description, jurisdiction }]`) | Active live rule matrix |
| `/api/v1/rule-snapshots/{id}` | `GET` | Authenticated | None | **200 OK**: `RuleSnapshotDto` (`{ snapshotId, jurisdiction, effectiveDate, status, snapshotDigest, rules: [...] }`)<br>**404 Not Found**: `{ code: "SNAPSHOT_NOT_FOUND" }` | Pinned governance snapshot (e.g. `snap-2026-q4-v1`). Digest computed via canonical SHA-256 over sorted rules. |

### 3.4 Optimization Jobs & Solvers (`optimization-service`)
- **Controller Source:** [`OptimizationController.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/optimization-service/src/main/java/org/soa8/freight/optimization/controller/OptimizationController.java)
- **Gateway Route:** `/api/v1/optimization-jobs/**` -> `lb://optimization-service`

| Endpoint | Method | Auth / Role | Request Schema | Response Schema & Codes | State Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/v1/optimization-jobs` | `POST` | Authenticated | `{ declarationId, compartment: { compartmentId, lengthMm, widthMm, heightMm, maxPayloadKg }, items: [...], ruleSnapshotId, timeLimitSeconds, algorithm, async }` | **202 Accepted**: `OptimizationJobEntity` (`{ jobId, declarationId, compartmentId, ruleSnapshotId, status, solverStatus, provenance, executionDurationMs, resultJson }`)<br>**400 Bad Request** / **422 Unprocessable** (if snapshot missing) | `algorithm`: `HEURISTIC_EP_3D_V1`, `PYTHON_ORTOOLS_CPSAT_3D_V1`, `PYTHON_CPSAT_3D_V1`, or `CONTROLLED_STUB`. If worker algorithm or `async=true`, dispatched via Kafka command topic. |
| `/api/v1/optimization-jobs/{id}/status` | `GET` | Authenticated | None | **200 OK**: `{ jobId, declarationId, compartmentId, ruleSnapshotId, status: ("SUBMITTED"\|"RUNNING"\|"COMPLETED"\|"FAILED"\|"TIMEOUT"), currentAttemptId, attemptCount, solverStatus, provenance, executionDurationMs, errorMessage, createdAt, completedAt }`<br>**404 Not Found** | **Lightweight polling endpoint**: Avoids transmitting heavy payload JSON during solve cycle. |
| `/api/v1/optimization-jobs/{id}/result` | `GET` | Authenticated | None | **200 OK**: `CandidateLayoutDto` (`{ layoutId, layoutVersion, compartmentId, contentDigest, solverStatus, provenance, ruleSnapshotId, executionDurationMs, placements: [...], unassignedItems: [...], diagnostics }`)<br>**409 Conflict**: `JOB_NOT_READY`<br>**422 Unprocessable**: If `FAILED` or `TIMEOUT` | Full 3D placement geometry and solver diagnostics. |
| `/api/v1/optimization-jobs/{id}` | `GET` | Authenticated | None | **200 OK**: Full `OptimizationJobEntity` including raw `resultJson` | General job entity query |

### 3.5 Compartment Assessment & Lifecycle Workflow (`compartment-service`)
- **Controller Source:** [`CompartmentController.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/compartment-service/src/main/java/org/soa8/freight/compartment/controller/CompartmentController.java)
- **Gateway Route:** `/api/v1/assessments/**`, `/api/v1/layouts/**` -> `lb://compartment-service`

| Endpoint | Method | Auth / Role | Request Schema | Response Schema & Codes | State Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/v1/assessments` | `POST` | Authenticated | `{ declarationId, declarationVersion, ruleSnapshotId, compartment: { compartmentId, lengthMm, widthMm, heightMm, maxPayloadKg } }` | **202 Accepted**: `AssessmentEntity` in `workflowStatus: "DRAFT"` with pinned `ruleSnapshotDigest`. | Pins immutable rule snapshot to assessment lifecycle. |
| `/api/v1/assessments/{id}` | `GET` | Authenticated | None | **200 OK**: `AssessmentEntity`<br>**404 Not Found** | State machine inspects `workflowStatus`: `DRAFT` -> `CANDIDATE_READY` -> `PENDING_APPROVAL` -> `APPROVED` \| `REJECTED`. |
| `/api/v1/assessments/{id}/candidate` | `POST` | Authenticated | `CandidateLayoutDto` (full layout payload) | **200 OK**: `AssessmentEntity` transitioned to `CANDIDATE_READY`.<br>**409 Conflict**: `DIGEST_MISMATCH` if content digest violates canonical SHA-256. | Persists candidate layout and records SHA-256 fingerprint in assessment. |
| `/api/v1/assessments/{id}/validation` | `POST` | Authenticated | `ValidationResultDto` (`{ validationId, passed, validatorVersion, evaluatedRulesCount, findings: [...] }`) | **200 OK**: `AssessmentEntity` transitioned to `PENDING_APPROVAL` (if passed) or blocks approval.<br>**409 Conflict**: `VALIDATION_MISMATCH` if snapshot or layout digest mismatch. | Authoritatively binds validation run to the assessment. |
| `/api/v1/assessments/{id}/approve` | `POST` | Load Planner (`ROLE_LOAD_PLANNER`) | `{ layoutVersion, expectedDigest, plannerId, notes }` | **200 OK**: `AssessmentEntity` in `workflowStatus: "APPROVED"`, `approvalDecision: "APPROVED"`.<br>**401 Unauthorized**: Missing/invalid JWT.<br>**403 Forbidden**: If missing `ROLE_LOAD_PLANNER`.<br>**409 Conflict**: `APPROVAL_BLOCKED` if validation failed or expected digest does not match candidate digest. | **Cryptographic Zero-Trust Gate**: Fails closed if digest tampered or validation unperformed. |
| `/api/v1/assessments/{id}/reject` | `POST` | Load Planner (`ROLE_LOAD_PLANNER`) | `{ layoutVersion, plannerId, rejectionReasonCode, notes }` | **200 OK**: `AssessmentEntity` in `workflowStatus: "REJECTED"`, `approvalDecision: "REJECTED"`.<br>**401 Unauthorized** / **403 Forbidden** | Writes `CargoLoadRejected` event to `outbox_events` table for Kafka broadcast. |
| `/api/v1/assessments/{id}/events` | `GET` | Authenticated | None | **200 OK**: `List<OutboxEvent>` (`[{ eventId, eventType, aggregateId, aggregateType, topic, payloadJson, status, createdAt }]`) | Audit log of transactional outbox events. |
| `/api/v1/assessments/{id}/layout` | `GET` | Authenticated | None | **200 OK**: `CandidateLayoutEntity` for currently active candidate version.<br>**404 Not Found**: `NO_CANDIDATE_LAYOUT` | Retrieves current candidate layout. |
| `/api/v1/layouts/{id}/versions/{v}`| `GET` | Authenticated | None | **200 OK**: `CandidateLayoutEntity` for specific version.<br>**404 Not Found** | Retrieves versioned layout audit history. |

### 3.6 Independent 3D Validation Engine (`validation-service`)
- **Controller Source:** [`ValidationController.java`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/validation-service/src/main/java/org/soa8/freight/validation/controller/ValidationController.java)
- **Gateway Route:** `/api/v1/validations/**` -> `lb://validation-service`

| Endpoint | Method | Auth / Role | Request Schema | Response Schema & Codes | State Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/v1/validations` | `POST` | Authenticated | `{ layout: CandidateLayoutDto, compartment: CompartmentDto, ruleSnapshot: RuleSnapshotDto }` | **200 OK**: `ValidationResultDto` (`{ validationId, passed: boolean, validatorVersion: "1.0.0-m1", evaluatedRulesCount, findings: [{ severity, code, message, involvedItemIds: string[] }] }`) | Evaluates AABB 3D non-overlap, containment, axle load distribution, stacking limits, and 3D Euclidean segregation distance. |
| `/api/v1/validations/{id}` | `GET` | Authenticated | None | **200 OK**: `ValidationResultDto`<br>**404 Not Found** | Historical validation audit retrieval. |

---

## 4. 3D Geometric Representation Viability Audit

Can the backend output drive a real-time 3D simulation?

1. **Compartment Envelope:**
   - [`CompartmentDto`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/common-contracts/src/main/java/org/soa8/freight/common/model/CompartmentDto.java): `lengthMm`, `widthMm`, `heightMm`, `maxPayloadKg`.
   - Full 3D bounding box is precisely specified.
2. **Item Placement Coordinates:**
   - [`ItemPlacementDto`](file:///c:/Users/ADMIN/Desktop/KL/SOA/Hackathon/common-contracts/src/main/java/org/soa8/freight/common/model/ItemPlacementDto.java):
     - Origin coordinates: `xMm` (length axis), `yMm` (width axis), `zMm` (vertical height axis from floor).
     - Bounding dimensions: `lengthMm`, `widthMm`, `heightMm`.
     - Stacking & weight: `weightKg`, `stackable: boolean`, `maxStackWeightKg: double`.
     - Orientation: `orientation` (`"DEFAULT"` or `"ROTATE_YAW_90"`).
3. **Solver & Candidate Metadata:**
   - `contentDigest`: SHA-256 canonical hash over sorted item placement positions.
   - `solverStatus`: `OPTIMAL`, `FEASIBLE`, `INFEASIBLE`, `UNKNOWN`, `TIMEOUT`.
   - `provenance`: `PYTHON_ORTOOLS_CPSAT_3D_V1`, `HEURISTIC_EP_3D_V1`, `CONTROLLED_STUB`.
   - `unassignedItems`: Explicit list of items rejected due to capacity or incompatibility, with `reasonCode` and `reasonDescription`.
4. **Validation Findings Grounding:**
   - `findings`: Array with `involvedItemIds`, `code` (`HAZARD_SEGREGATION_VIOLATION`, `OVERLAP_DETECTED`, `CONTAINMENT_VIOLATION`, `MAX_STACK_WEIGHT_EXCEEDED`).
   - Allows the 3D simulation to highlight conflicting bounding boxes in bright warning colors and render 3D Euclidean safety buffer spheres/lines between incompatible hazard classes.

**Conclusion:** The backend contracts completely supply all geometric, spatial, physical, and audit data required to render a 3D digital twin.

---

## 5. Identified API Gaps & Recommendations

| Item | Current State | Frontend Impact | Recommended Approach (Minimal, Safe) |
| :--- | :--- | :--- | :--- |
| **Declarations Listing** | `GET /api/v1/declarations` is not implemented (only `GET /api/v1/declarations/{id}`). | Shipper cannot browse past declarations in a list table; must know ID or create new. | In Phase G.2, add a simple `GET /api/v1/declarations` in `CargoDeclarationController` returning `List<CargoDeclaration>` filtered by optional `shipperId`. |
| **Assessments Listing** | `GET /api/v1/assessments` is not implemented (only `GET /api/v1/assessments/{id}`). | Planner cannot browse pending assessments queue; must know ID. | In Phase G.2, add `GET /api/v1/assessments` in `CompartmentController` returning active assessments by `workflowStatus`. |
| **Layout Entity vs DTO** | `GET /api/v1/assessments/{id}/layout` returns `CandidateLayoutEntity` which stores `placementsJson` as a string rather than parsed `placements` array. | Frontend would have to `JSON.parse(entity.placementsJson)`. | Frontend should retrieve the candidate layout directly via `GET /api/v1/optimization-jobs/{id}/result` (returns parsed `CandidateLayoutDto`) or handle JSON parsing if querying `/layout`. |

*Note:* Neither gap blocks building the vertical slice workflow because each step returns the created object ID, allowing continuous end-to-end flow. Adding the two `findAll` endpoints in Phase G.2 will provide full dashboard navigation.

---

## 6. Recommended Frontend Architecture (Phase G.2)

### 6.1 Technology Stack
- **Framework:** React 18.2 + TypeScript 5.2
- **Build Tool:** Vite 5.1 (existing proxy configuration to `http://localhost:8080`)
- **Navigation:** `react-router-dom` (v6.22+)
- **Styling:** Structured CSS design system with CSS custom properties (dark glassmorphism theme matching logistics command center aesthetics; avoiding heavyweight UI frameworks).
- **Icons:** `lucide-react` (already installed and verified in `package.json`).
- **3D Engine (Phase G.3):** Three.js + `@react-three/fiber` + `@react-three/drei` (clean integration for canvas-based 3D packaging simulation with orbit controls and bounding box meshes).
- **State Management:** Lightweight React Context (`AuthContext` for JWT tokens/roles, `WorkflowContext` for active declaration/assessment session) + custom hooks with typed API client.

### 6.2 Proposed Directory Structure
```
frontend/src/
├── api/                     # Strongly-typed API client modules matching backend DTOs
│   ├── client.ts            # Base fetch wrapper (token injection, 401 interceptor)
│   ├── authApi.ts           # Login, me, token refresh
│   ├── declarationApi.ts    # Create, get, workflow projection
│   ├── rulesApi.ts          # Rules, rule-snapshots, digests
│   ├── optimizationApi.ts   # Submit job, poll status, fetch result
│   ├── assessmentApi.ts     # Create, record candidate, approve, reject
│   └── validationApi.ts     # Independent validation execution
├── types/                   # TypeScript interfaces matching common-contracts
│   ├── auth.ts
│   ├── declaration.ts
│   ├── rules.ts
│   ├── optimization.ts
│   ├── assessment.ts
│   └── validation.ts
├── context/                 # Global application contexts
│   ├── AuthContext.tsx      # Token persistence, login, logout, active role
│   └── NotificationContext.tsx # Banner and toast alerts for API errors
├── hooks/                   # Custom reusable hooks
│   ├── useAuth.ts
│   ├── useOptimizationJob.ts # Resilient status polling hook
│   └── useAssessmentWorkflow.ts
├── components/              # Modular UI components
│   ├── common/              # Buttons, Badges, Modals, StatusPills, NavHeader
│   ├── declaration/         # Item input form, declaration card
│   ├── rules/               # Rule snapshot badge, segregation matrix table
│   ├── validation/          # Validation outcome card, findings list, zero-trust audit
│   ├── planner/             # Approval modal, rejection modal, digest check
│   └── simulation/          # 2D table fallback & 3D compartment viewer
├── pages/                   # Top-level view pages
│   ├── LoginPage.tsx        # Sign-in with role pre-sets (Shipper, Load Planner)
│   ├── ShipperPortalPage.tsx# Cargo declaration authoring & tracking
│   ├── PlannerReviewPage.tsx# Assessment queue, validation audit, decision controls
│   └── LayoutViewerPage.tsx # Dual 3D simulation / 2D table layout viewer
├── styles/                  # Design system tokens and global CSS
│   ├── tokens.css           # Logistics palette, typography, radii, shadows
│   └── global.css
├── App.tsx                  # Main router and shell layout
└── main.tsx
```

---

## 7. UI & Integration Safety Requirements

1. **State Transparency:** The UI will distinctly render: `SUBMITTED`, `RUNNING`, `COMPLETED`, `FAILED`, `TIMEOUT`, `UNKNOWN`, `INFEASIBLE`, and `VALIDATION_FAILED`.
2. **Zero-Trust Validation Gating:** An unvalidated or failed candidate layout will **never** display an active "Approve" button. The UI will explicitly display the validation findings and explain why approval is blocked.
3. **Cryptographic Integrity Display:**
   - Display the pinned `ruleSnapshotId` and rule snapshot SHA-256 digest.
   - Display the `candidateContentDigest` and verify that approval requests include this exact digest.
4. **Accessible Fallback:** An interactive SVG 2D floor-plan and comprehensive tabulated placement list will accompany the 3D viewport, guaranteeing usability on low-power devices and supporting screen-reader keyboard navigation.
5. **Role-Enforced UI:** The UI will hide planner decision actions from users logged in with `ROLE_SHIPPER`, while recognizing that backend Spring Security role checks are the authoritative enforcement point.

---

## 8. Implementation Order for Subsequent Phases

1. **Phase G.1 (Current):** Reconnaissance, contract inventory, and architectural plan.
2. **Phase G.2:** Modular frontend foundation:
   - Install `react-router-dom`.
   - Implement typed API client (`src/api/*`) and types (`src/types/*`).
   - Implement `AuthContext` and Login view with role-switching.
   - Implement Shipper declaration page and Load Planner decision view with live backend integration.
   - Refactor existing monolith `App.tsx` into clean component architecture.
3. **Phase G.3:** 3D Load Simulation & Visualizer:
   - Install Three.js and `@react-three/fiber`.
   - Render 3D compartment wireframe (12,000 × 2,400 × 2,600 mm trailer).
   - Render cargo bounding boxes with color-coded hazard classes, stack indicators, and Euclidean segregation buffer planes.
   - Integrate 2D tabular fallback and verification audit.

---

## 9. Verification Summary

- **Vite Build Verification:** Passed without error (`npm run build` completed in 9.71s).
- **Backend Endpoints Inspected:** 17 distinct REST endpoints across 6 microservices verified in source code.
- **Repository Working Tree:** Preserved without destructive changes.
- **Commit Status:** Report documented cleanly in `docs/implementation/phase-g1-reconnaissance.md`.
