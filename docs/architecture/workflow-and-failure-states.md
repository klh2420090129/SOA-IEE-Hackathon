# Workflow State Machine & Failure State Model

## 1. Assessment Lifecycle State Machine

The **Compartment Service** orchestrates the business workflow for cargo co-loading assessments. Every state transition is recorded in the assessment audit log.

```
       +---------------------------------------------+
       |                   DRAFT                     |
       +---------------------------------------------+
                              |
                     [Start Assessment]
                              v
       +---------------------------------------------+
       |                 OPTIMIZING                  |
       +---------------------------------------------+
           |                  |                   |
    [Stub/Solver OK]   [Solver Infeasible]   [Solver Timeout/Error]
           v                  v                   v
+--------------------+ +--------------------+ +--------------------+
|  CANDIDATE_READY   | |     INFEASIBLE     | |    SOLVER_ERROR    |
+--------------------+ +--------------------+ +--------------------+
         |
    [Run Independent Validation]
         |
         +------------------------------------+
         |                                    |
  [Validation Passes]                 [Validation Fails]
         v                                    v
+--------------------+               +--------------------+
|  PENDING_APPROVAL  |               | VALIDATION_FAILED  |
+--------------------+               +--------------------+
    |            |
 [Approve]    [Reject]
    v            v
+----------+ +----------+
| APPROVED | | REJECTED |  --> Emits CargoLoadRejected event
+----------+ +----------+
```

### Explicit State Definitions

1. **`DRAFT`:**
   - Assessment created and bound to explicit `declarationId`, `declarationVersion`, and `ruleSnapshotId`.
   - Modifiable prior to dispatch.
2. **`OPTIMIZING`:**
   - Asynchronous optimization job dispatched. Awaiting candidate layout.
3. **`CANDIDATE_READY`:**
   - Candidate layout generated with item coordinates `(x, y, z)` and orientations.
   - Candidate SHA-256 digest computed.
   - **Not approved:** Must undergo independent validation.
4. **`PENDING_APPROVAL`:**
   - Independent validation passed with zero critical violations.
   - Ready for human Load Planner review.
5. **`APPROVED`:**
   - Authorized load planner submitted approval.
   - Binds directly to the immutable candidate version, content digest, declaration version, and validation record.
6. **`REJECTED`:**
   - Candidate rejected by load planner (or auto-rejected due to unresolvable conflict).
   - Structured rejection codes recorded (e.g., `HAZARD_INCOMPATIBILITY`, `EXCEEDS_LOCAL_LIMITS`, `OPERATIONAL_OVERRIDE`).
   - Triggers `CargoLoadRejected` event emission via transactional outbox.
7. **`VALIDATION_FAILED` (Blocking State):**
   - Independent validator discovered geometric overlap, out-of-bounds, capacity breach, or hazard segregation violation.
   - Cannot transition to `PENDING_APPROVAL` or `APPROVED`.
8. **`BLOCKED_UNVERIFIED_RULES` (Blocking State):**
   - Referenced rule snapshot is unverified, missing, or unsupported.
   - Assessment is permanently halted until verified rules are provided.

---

## 2. Invalidation & Immutability Rules

- **Digest Protection:** Each candidate layout has a computed `contentDigest` (SHA-256 over canonical sorted items: ID, dimensions, coordinates, orientation).
- **No Stale Approvals:** Any change in cargo items, quantities, or rule snapshots invalidates previous candidate layouts. Approval can never be granted to a stale or modified candidate.
- **Fail Closed:** If any safety or geometric check encounters an ambiguous or missing condition, the candidate is placed into a failed/blocked state.
