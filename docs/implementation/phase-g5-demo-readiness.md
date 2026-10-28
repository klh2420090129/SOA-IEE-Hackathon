# Phase G.5 — Final Browser Demo Rehearsal and Release Readiness Report

**Repository:** `https://github.com/klh2420090129/SOA-IEE-Hackathon`  
**Target Branch:** `feat/milestone-1-vertical-slice`  
**Starting / Audited Commit:** `27c986f` (`docs(audit): complete Phase G.4 end-to-end integration and demo hardening report`)  
**Audit & Rehearsal Timestamp:** October 28, 2026  
**Auditor:** SOA Team 8 Full-Stack Integration & QA Engineer  

---

## 1. Verified Repository State & Safety Baseline

- **Current Git Branch:** `feat/milestone-1-vertical-slice` (in sync with remote).
- **Baseline Git Commit:** `27c986f`.
- **Working Tree Integrity:** Clean working directory. No destructive operations (`git reset`, `git clean`) were performed. All persistent database data and Docker volumes remain intact.
- **Audited Deliverables Verified:**
  - `docs/implementation/phase-g1-reconnaissance.md`
  - `docs/implementation/phase-g2-frontend-modularization.md`
  - Phase G.3 3D Simulator Implementation (`CandidateViewer3D.tsx`, `coordinateUtils.ts`)
  - `docs/implementation/phase-g4-integration-audit.md`

---

## 2. Browser Verification Gap & Automation Environment Audit

### 2.1 Investigation of Browser Automation Infrastructure
An environment inspection was conducted to determine available browser automation tooling:
1. **Installed Browser Binaries:**
   - Google Chrome: `C:\Program Files\Google\Chrome\Application\chrome.exe` (Present)
   - Microsoft Edge: `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe` (Present)
2. **Automation Drivers in Local Workspace:**
   - Neither Selenium (`selenium` Python package) nor local Playwright Node.js bindings (`node_modules/playwright`) are installed in the offline workspace environment.
   - External network package downloads (`pip install`, `npm install playwright`, or Playwright driver binary zips from Azure/Akamai CDNs) fail or are prohibited to prevent unverified network calls in the sandbox.
   - Directly invoking Chrome in headless `--dump-dom` mode on dynamic client-side Single Page Applications (SPAs) does not emulate interactive React user input (button clicks, form submits, WebGL canvas events).

### 2.2 Truthful Reporting & Verification Distinction
In strict compliance with the Phase G.5 objective:
- **We do NOT claim that interactive headless browser automation ran or passed.**
- **Automated Unit & Integration Verification:** Confirmed via Vitest DOM tests (23 tests passed, including `PlannerReviewPage.test.tsx` verifying component lookup, state loading, and 2D/3D toggle switching).
- **Service Integration Pipeline:** Confirmed via `run-demo.ps1` and `verify-cpsat-and-compare.ps1` exercising all REST endpoints and Kafka events through the API Gateway.
- **Browser-Level Verification:** Provided below as an **honest, actionable, step-by-step manual test checklist** with exact UI expectations for live demonstration execution.

---

## 3. Rendered Geometry & Physical Safety Semantics Audit

Using the real candidate returned by the backend (`scenario-a-03-layout-result.json` from `demo-20261028-101326`):

```json
{
  "layoutId": "ade3f05f-c0ae-41e2-b5d8-040bdcf09aa9",
  "contentDigest": "9e0f0bbc8e741f8c74a85c380979c2f888402f463295f0f18d38961b950f2e38",
  "solverStatus": "FEASIBLE",
  "provenance": "HEURISTIC_EP_3D_V1",
  "placements": [
    {
      "cargoItemId": "item-class3-solv",
      "hazardClass": "3",
      "unNumber": "UN1993",
      "lengthMm": 1200, "widthMm": 800, "heightMm": 1000,
      "xMm": 0, "yMm": 0, "zMm": 0
    },
    {
      "cargoItemId": "item-class8-acid",
      "hazardClass": "8",
      "unNumber": "UN1789",
      "lengthMm": 1000, "widthMm": 800, "heightMm": 900,
      "xMm": 0, "yMm": 800, "zMm": 0
    }
  ]
}
```

### Physical Alignment Verification:
1. **Floor Elevation & Ground Plane:**
   - In `coordinateUtils.ts`, Three.js center $Y = (z_{\text{mm}} \times 0.001) + (\text{height}_{\text{m}} / 2)$.
   - When $z_{\text{mm}} = 0$, the bottom face of the cargo cuboid sits exactly at $Y = 0$, resting flush on the dark trailer floor mesh (`position={[0, -h/2 + 0.005, 0]}`).
2. **Door & Cab Orientation:**
   - Longitudinal axis extends from $X = -\text{compL}/2$ (Front Cab, text label `"Origin (0,0) - Cab/Front"`) to $X = +\text{compL}/2$ (Rear Doors, text label `"Rear Doors (12000mm)"`).
3. **Lateral Placement & Segregation:**
   - Item 1 (`item-class3-solv`, Class 3) is placed at $y_{\text{mm}} = 0$ (far left wall).
   - Item 2 (`item-class8-acid`, Class 8) is placed at $y_{\text{mm}} = 800$ (adjacent across floor).
   - Rendered distance matches physical millimeters.
4. **Safety & Zero-Trust Governance:**
   - The 3D view renders visual bounding boxes and hazard colors (Class 3 Amber `#d97706`, Class 8 Slate `#475569`).
   - If an item has validation findings, `violationItemIds.has(box.cargoItemId)` triggers an unmistakable crimson highlight (`#ef4444`) with high-contrast alert borders (`#fee2e2`).
   - The 3D canvas **never** declares a layout safe. Authoritative safety is strictly derived from the independent Zero-Trust Validation Engine and Load Planner sign-off against the statutory rule snapshot.

---

## 4. End-to-End Demo Rehearsal Script & Manual Checklist

When conducting the live demonstration on `http://localhost:5173`:

### Step 1: Shipper Workflow
1. Navigate to `http://localhost:5173`.
2. On the **Freight Platform Authentication** screen, select **Shipper** persona (auto-populates `shipper@soa8.org`).
3. Click **Sign In**.
4. Confirm navigation to **Shipper Declarations**.
5. Observe the declaration submission form pre-loaded with:
   - Origin: `JNPT Port Mumbai`
   - Destination: `Tughlakabad Freight Terminal Delhi`
   - Items: `ITEM-SOLVENT-01` (Class 3, UN1993) and `ITEM-EXPLOSIVE-02` (Class 1, UN0004).
6. Click **Submit Cargo Declaration**.
7. Confirm that the Declaration is created with status `SUBMITTED`, and copy the generated **Declaration ID** (e.g. `f16a81ee-...`).

### Step 2: Load Planner Session & Assessment
1. In the navigation header, use the quick role switcher to click **Planner** (switches session to `planner@soa8.org`, `ROLE_LOAD_PLANNER`).
2. Navigate to **Load Planner Console**.
3. In the session bar, paste the Declaration ID and click **Start New Assessment**.
4. Confirm:
   - Assessment is created in `compartment-service` with status `DRAFT`.
   - Associated rule snapshot is pinned as `snap-2026-q4-v1`.

### Step 3: Optimization & Worker Dispatch
1. In the Algorithm dropdown, select **OR-Tools CP-SAT Worker (PYTHON_CPSAT_3D_V1)** or **Heuristic 3D (HEURISTIC_EP_3D_V1)**.
2. Click **Run Optimization**.
3. Observe real-time job status polling: `SUBMITTED` $\to$ `COMPLETED`.
4. Observe candidate layout recording in the assessment, followed automatically by the **Independent Zero-Trust 3D Validation Engine** execution.
5. If items are compatible:
   - Validation banner displays: `VERIFIED PASSED (0 violations)`.
   - Workflow Status transitions to: `PENDING_APPROVAL`.

### Step 4: 3D Simulator & 2D Accessible Fallback Inspection
1. Confirm the 3D Canvas mounts and displays:
   - Semi-transparent trailer bounding cage (`12000 x 2400 x 2600 mm`).
   - Coordinate axis labels: `"Origin (0,0) - Cab/Front"` and `"Rear Doors"`.
   - Colored cargo cuboids positioned according to solver placements.
2. Test Camera Preset buttons: **Isometric**, **Top-Down**, **Side**, and **Rear**.
3. Click on a cargo box in 3D:
   - Overlay card appears showing exact coordinates (`x`, `y`, `z` mm), bounding dimensions, orientation, and weight.
4. Click **Play Sequence**:
   - Illustrative loading sequence animates boxes descending into the container.
5. Toggle to **2D Floor-Plan & Table Fallback**:
   - Confirm SVG top-down projection and structured accessible coordinate table are displayed.

### Step 5: Authoritative Approval & Rejection Decisions
1. **Approval Flow (Scenario A):**
   - Click **Authorize & Approve Layout**.
   - Confirm assessment status transitions to `APPROVED`.
2. **Rejection Flow (Scenario B):**
   - Create or lookup another assessment, click **Operational Rejection**.
   - Select reason code (e.g. `OPERATIONAL_CAPACITY_HOLD`) and click **Confirm Operational Rejection**.
   - Confirm assessment transitions to `REJECTED`, and downstream Kafka event projection updates declaration status to `REJECTED`.

---

## 5. Startup & Shutdown Procedures

### Startup Sequence:
```powershell
# 1. Start Docker Middleware
docker compose up -d

# 2. Start Eureka Discovery
mvn -pl service-discovery spring-boot:run

# 3. Start API Gateway
mvn -pl api-gateway spring-boot:run

# 4. Start Domain Services
mvn -pl auth-service spring-boot:run
mvn -pl cargo-declaration-service spring-boot:run
mvn -pl compatibility-rule-service spring-boot:run
mvn -pl compartment-service spring-boot:run
mvn -pl optimization-service spring-boot:run
mvn -pl validation-service spring-boot:run

# 5. Start Python Worker
python workers/optimizer-worker/worker.py

# 6. Start Frontend
cd frontend
npm run dev
```

### Shutdown Sequence:
Terminate the terminal sessions or background tasks running the services, and stop Docker containers with `docker compose stop`.
