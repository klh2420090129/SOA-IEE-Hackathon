# REST API Contracts

All endpoints use `/api/v1` prefix. All timestamps are ISO-8601 UTC. All IDs are UUID strings unless otherwise specified.

---

## 1. Auth Service (`auth-service`)

### `POST /api/v1/auth/login`
- **Caller:** Shippers, Load Planners, Admins
- **Auth:** Public
- **Request Body:**
```json
{
  "username": "planner@soa8.org",
  "password": "Password123!"
}
```
- **Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "userId": "d7a3f4e2-89b1-4c3e-9f0a-1b2c3d4e5f6a",
  "username": "planner@soa8.org",
  "roles": ["ROLE_LOAD_PLANNER"]
}
```

### `GET /api/v1/auth/me`
- **Auth:** Bearer Token
- **Response `200 OK`:** Current user profile and granted roles.

---

## 2. CargoDeclaration Service (`cargo-declaration-service`)

### `POST /api/v1/declarations`
- **Caller:** Shipper (`ROLE_SHIPPER`)
- **Request Body:**
```json
{
  "referenceNumber": "DEC-2026-001",
  "shipperId": "shipper-uuid",
  "origin": "Mumbai Logistics Hub",
  "destination": "Delhi Freight Terminal",
  "items": [
    {
      "cargoItemId": "item-001",
      "description": "Industrial Solvent Drums",
      "hazardClass": "3",
      "unNumber": "UN1993",
      "lengthMm": 1200,
      "widthMm": 800,
      "heightMm": 1000,
      "weightKg": 450,
      "stackable": true,
      "maxStackWeightKg": 1000,
      "quantity": 2
    }
  ]
}
```
- **Response `201 Created`:** Full declaration resource with `version: 1`, `status: "SUBMITTED"`.

### `GET /api/v1/declarations/{id}`
- **Auth:** `ROLE_SHIPPER` (owner only) or `ROLE_LOAD_PLANNER`

### `GET /api/v1/declarations/{id}/workflow`
- **Auth:** `ROLE_SHIPPER` / `ROLE_LOAD_PLANNER`
- **Response `200 OK`:** Projection of latest assessment outcome (e.g. `REJECTED`, reason codes, notes).

---

## 3. CompatibilityRule Service (`compatibility-rule-service`)

### `GET /api/v1/rules`
- **Auth:** Authenticated
- **Response `200 OK`:** Active list of typed compatibility rules.

### `GET /api/v1/rule-snapshots/{id}`
- **Auth:** Authenticated
- **Response `200 OK`:** Immutable published snapshot containing rules, effective date, jurisdiction, and SHA-256 digest.

### `POST /api/v1/rule-snapshots/publish`
- **Auth:** `ROLE_ADMIN` (Governance only)

---

## 4. Compartment Service (`compartment-service`)

### `POST /api/v1/assessments`
- **Caller:** Load Planner (`ROLE_LOAD_PLANNER`)
- **Request Body:**
```json
{
  "declarationId": "b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e",
  "declarationVersion": 1,
  "ruleSnapshotId": "snap-2026-q4-v1",
  "compartment": {
    "compartmentId": "truck-comp-01",
    "name": "Standard 40ft High-Cube Trailer",
    "lengthMm": 12000,
    "widthMm": 2400,
    "heightMm": 2600,
    "maxPayloadKg": 25000
  }
}
```
- **Response `202 Accepted`:** Assessment resource initialized in `OPTIMIZING` state.

### `GET /api/v1/assessments/{id}`
- **Auth:** `ROLE_LOAD_PLANNER` / `ROLE_SHIPPER`
- **Response `200 OK`:** Full assessment details, state, candidate layout reference, validation findings.

### `POST /api/v1/assessments/{id}/approve`
- **Auth:** `ROLE_LOAD_PLANNER`
- **Request Body:**
```json
{
  "layoutVersion": 1,
  "expectedDigest": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "approvalNotes": "Valid co-load verified under CMV rule set."
}
```
- **Response `200 OK`:** State transitioned to `APPROVED`.

### `POST /api/v1/assessments/{id}/reject`
- **Auth:** `ROLE_LOAD_PLANNER`
- **Request Body:**
```json
{
  "layoutVersion": 1,
  "rejectionReasonCode": "HAZARD_SEGREGATION_VIOLATION",
  "rejectionNotes": "Explosive and flammable materials cannot be loaded within 3000mm."
}
```
- **Response `200 OK`:** State transitioned to `REJECTED`, outbox event enqueued for Kafka publication.

---

## 5. Optimization Service (`optimization-service`)

### `POST /api/v1/optimization-jobs`
- **Request Body:** Declaration items, compartment dimensions, timeout seconds.
- **Response `202 Accepted`:** Optimization job with state `RUNNING`.

### `GET /api/v1/optimization-jobs/{id}`
- **Response `200 OK`:** Job status (`COMPLETED`, `INFEASIBLE`, etc.).

### `GET /api/v1/optimization-jobs/{id}/result`
- **Response `200 OK`:** Placements with coordinates `(x, y, z)`, dimensions, orientation, and explicit `provenance: "CONTROLLED_STUB"`.

---

## 6. Validation Service (`validation-service`)

### `POST /api/v1/validations`
- **Request Body:** Candidate placements, compartment specification, rule snapshot.
- **Response `200 OK`:**
```json
{
  "validationId": "val-uuid-99",
  "passed": false,
  "validatorVersion": "1.0.0-m1",
  "evaluatedRulesCount": 3,
  "findings": [
    {
      "severity": "CRITICAL",
      "code": "HAZARD_SEGREGATION_VIOLATION",
      "message": "Item item-001 (Class 1) is within 1200mm of item-002 (Class 3). Minimum required distance is 3000mm.",
      "involvedItemIds": ["item-001", "item-002"]
    }
  ]
}
```
