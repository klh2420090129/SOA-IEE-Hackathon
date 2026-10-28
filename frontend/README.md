# SOA Team 8 — Hazardous Cargo Frontend Application

React 18 + TypeScript + Vite web application for the Hazardous Cargo Co-Loading and 3D Load Planning platform.

---

## 1. Architecture & Module Structure

```
src/
├── api/                   # Strongly typed domain API clients
│   ├── client.ts          # Base fetch wrapper, JWT bearer injection, 401 interceptor
│   ├── authApi.ts         # Authentication & role identification
│   ├── declarationApi.ts  # Cargo consignment declaration & workflow projection
│   ├── rulesApi.ts        # Compatibility rules & immutable snapshots
│   ├── optimizationApi.ts # Job dispatch, resilient polling, candidate retrieval
│   ├── assessmentApi.ts   # Assessment orchestrator, candidate binding, approval/rejection
│   └── validationApi.ts   # Independent 3D zero-trust validation
├── context/
│   └── AuthContext.tsx    # Session management, token persistence, role checking
├── hooks/
│   └── useOptimizationJob.ts # Resilient status polling with backoff & user cancellation
├── components/
│   ├── common/
│   │   └── NavigationHeader.tsx # Header ribbon with persona switching
│   └── simulation/
│       └── CandidateViewer2D.tsx # Scaled SVG compartment viewer & tabular fallback
├── pages/
│   ├── LoginPage.tsx          # Role-based sign-in
│   ├── ShipperPortalPage.tsx  # Cargo declaration authoring & tracking
│   ├── PlannerReviewPage.tsx  # Load planner assessment, solver execution, decision
│   └── RulesAndAuditPage.tsx  # Safety matrix, snapshot digest, outbox events
├── types/
│   └── api.ts             # TypeScript interfaces matching backend DTOs
├── App.tsx                # Main router & role shell
├── index.css              # Dark command-center logistics theme
└── main.tsx
```

---

## 2. Running Locally

### Development Server
```bash
cd frontend
npm install
npm run dev
```
The dev server starts on `http://localhost:5173`. Requests to `/api/*` are automatically reverse-proxied to the Spring Cloud API Gateway at `http://localhost:8080`.

### Building for Production
```bash
npm run build
```
Typechecks with `tsc` and bundles with Vite into `dist/`.

### Running Tests
```bash
npm test
```
Executes the Vitest test suite (`client.test.ts`, `useOptimizationJob.test.ts`, `CandidateViewer2D.test.tsx`).

---

## 3. Seed Personas & Credentials

- **Load Planner (`ROLE_LOAD_PLANNER`):** `planner@soa8.org` / `PlannerPass123!`
- **Shipper (`ROLE_SHIPPER`):** `shipper@soa8.org` / `ShipperPass123!`
