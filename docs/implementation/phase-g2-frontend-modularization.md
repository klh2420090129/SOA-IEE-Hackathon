# Phase G.2 — Modular React Frontend & Real Workflow Integration Report
**Project:** Hazardous Cargo Co-Loading and 3D Load Planning System (SOA Team 8)  
**Repository:** `https://github.com/klh2420090129/SOA-IEE-Hackathon`  
**Working Branch:** `feat/milestone-1-vertical-slice`  
**Baseline Git Commit:** `35bf3ca`  
**Date:** 2026-10-28  

---

## 1. Executive Summary

Phase G.2 successfully transformed the initial 1,205-line monolith proof of concept in `frontend/src/App.tsx` into a maintainable, modular, and strongly-typed React 18 + TypeScript web application.

The frontend is structured around genuine backend microservices endpoints and Spring Boot contracts verified in Phase G.1. It supports:
- Authentication & role-aware UI controls (`ROLE_SHIPPER` vs `ROLE_LOAD_PLANNER`).
- Shipper consignment authoring, item classification, and downstream Kafka projection tracking.
- Load Planner assessment lifecycle, algorithm selection (`HEURISTIC_EP_3D_V1` vs `PYTHON_CPSAT_3D_V1`), and asynchronous polling.
- Independent zero-trust 3D validation audit before approval.
- Cryptographic SHA-256 candidate digest verification and fail-closed approval gate.
- Rejection reason submission and transactional outbox event monitoring.
- Scaled 2D SVG cargo twin viewer with an accessible tabular coordinate fallback.

---

## 2. Implemented Architecture & Source Modules

```
frontend/src/
├── types/
│   └── api.ts                 # Full DTO typings matching backend Java classes
├── api/
│   ├── client.ts              # Fetch wrapper, Bearer injection, 401 interceptor
│   ├── client.test.ts         # 4 unit tests verifying tokens, 401s, 409s, and 503s
│   ├── authApi.ts             # POST /login, GET /me
│   ├── declarationApi.ts      # POST /declarations, GET /declarations/{id}, GET /workflow
│   ├── rulesApi.ts            # GET /rules, GET /rule-snapshots/{id}
│   ├── optimizationApi.ts     # POST /optimization-jobs, GET /status, GET /result
│   ├── assessmentApi.ts       # POST /assessments, /candidate, /validation, /approve, /reject
│   └── validationApi.ts       # POST /validations, GET /validations/{id}
├── context/
│   └── AuthContext.tsx        # React Context with user credentials, tokens, and role gates
├── hooks/
│   ├── useOptimizationJob.ts  # Resilient status polling hook with backoff & user cancellation
│   └── useOptimizationJob.test.ts # 3 unit tests verifying polling lifecycle
├── components/
│   ├── common/
│   │   └── NavigationHeader.tsx   # Header ribbon, active tabs, and persona switcher
│   └── simulation/
│       ├── CandidateViewer2D.tsx  # Scaled SVG compartment viewer & tabular coordinate fallback
│       └── CandidateViewer2D.test.tsx # 4 unit tests verifying 2D layout & validation indicators
├── pages/
│   ├── LoginPage.tsx          # Sign-in page with preset personas
│   ├── ShipperPortalPage.tsx  # Cargo declaration authoring & tracking
│   ├── PlannerReviewPage.tsx  # Assessment management, solver execution, and decisions
│   └── RulesAndAuditPage.tsx  # Safety matrix, snapshot digest, outbox events
├── App.tsx                    # Clean application shell with role-aware tab navigation
├── vite.config.ts             # Vite proxy configuration + Vitest test setup
└── package.json               # Added vitest and testing-library dependencies
```

---

## 3. Real Workflow & Security Verification

1. **Role Boundary Enforcement:**
   - The UI distinguishes `ROLE_SHIPPER` and `ROLE_LOAD_PLANNER`.
   - The "Authorize & Approve Layout" and "Operational Rejection" buttons are strictly hidden and disabled unless the authenticated user holds `ROLE_LOAD_PLANNER`.
   - The backend Spring Security authorization on `POST /api/v1/assessments/{id}/approve` remains the authoritative security boundary.
2. **Zero-Trust Validation Gating:**
   - The approval action is locked until an independent validation run is authoritatively bound to the assessment and returns `passed: true`.
   - If validation fails (e.g. IMDG hazard segregation violated), the UI displays the violation codes, involved items, and explicitly explains that approval is blocked.
3. **Cryptographic Digest Verification:**
   - The Load Planner approval request automatically submits the candidate's canonical SHA-256 `expectedDigest`.
   - Attempting approval with a mismatched digest triggers an HTTP 409 Conflict rejection in the backend.
4. **Asynchronous Polling & Cancellation:**
   - The `useOptimizationJob` hook queries `GET /api/v1/optimization-jobs/{id}/status` periodically, fetching the full candidate layout via `GET /result` only upon `COMPLETED`.
   - Provides an immediate "Cancel Polling" capability for long-running jobs.

---

## 4. Test & Verification Results

### Frontend Verification
1. **TypeScript Compilation & Production Bundle:**
   ```bash
   npm run build
   ```
   - **Result:** Success. Transformed 1485 modules in 3.04s. `tsc` passed with 0 errors.
2. **Automated Unit & Component Tests:**
   ```bash
   npm test
   ```
   - **Result:** **11 / 11 tests passed in 2.76s**.
   - `client.test.ts` (4/4 passed): Token injection, HTTP 401 session expiry, HTTP 409 conflict extraction, network error handling.
   - `useOptimizationJob.test.ts` (3/3 passed): Polling progression to completion, fast failure on `FAILED` status, user cancellation.
   - `CandidateViewer2D.test.tsx` (4/4 passed): Empty state rendering, coordinate plotting, unvalidated status display, segregation violation highlighting.

---

## 5. Remaining Limitations & Phase G.3 Transition

1. **Dashboard Listing Endpoints:**
   - As documented in Phase G.1, general `GET /api/v1/declarations` and `GET /api/v1/assessments` list endpoints are not part of the backend API.
   - The frontend uses ID-based lookups and provides convenient "Copy ID" clipboard shortcuts to transition between the Shipper and Load Planner views.
2. **3D Visualization:**
   - A clean component boundary has been established in `src/components/simulation/CandidateViewer2D.tsx`.
   - Phase G.3 will introduce Three.js and `@react-three/fiber` to accompany the 2D SVG viewer with an interactive 3D digital twin.
