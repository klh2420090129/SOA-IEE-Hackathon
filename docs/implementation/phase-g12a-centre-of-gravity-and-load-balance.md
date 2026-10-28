# Phase G.12A — Centre of Gravity (CoG) and Load-Balance Feasibility Report

**Repository**: `klh2420090129/SOA-IEE-Hackathon`
**Branch**: `feat/milestone-1-vertical-slice`
**Phase**: G.12A

---

## 1. Audit: Existing Assumptions vs Physical Parameter Gaps

### Existing Supported Contracts & Models
1. **Coordinate System & Origin**:
   - **Origin $(0, 0, 0)$**: Front/Cab-Left corner on the compartment floor.
   - **Longitudinal Axis ($X$)**: $0 \to \text{lengthMm}$ (front/cab to rear doors).
   - **Lateral Axis ($Y$)**: $0 \to \text{widthMm}$ (left to right wall).
   - **Vertical Axis ($Z$)**: $0 \to \text{heightMm}$ (floor surface upwards).
   - **Units**: Dimensions and positions in millimetres ($\text{mm}$); mass in kilograms ($\text{kg}$).
   - **Placement Geometry**: Coordinates $(x, y, z)$ define the minimum corner of the item's axis-aligned bounding box (AABB).
   - **Handling Unit Center Point**:
     $$x_i = x_{min,i} + \frac{L_i}{2.0}, \quad y_i = y_{min,i} + \frac{W_i}{2.0}, \quad z_i = z_{min,i} + \frac{H_i}{2.0}$$
     where $(L_i, W_i, H_i)$ are the placed package dimensions reflecting orientation (`DEFAULT` vs `ROTATE_YAW_90`).

2. **Existing Validated Constraints**:
   - Total payload limit: $\sum m_i \le \text{maxPayloadKg}$.
   - 3D AABB non-overlap: Strict pairwise interior bounding intersection.
   - Floor containment: $x_i \ge 0, y_i \ge 0, z_i \ge 0, x_i + L_i \le L_{\text{comp}}, y_i + W_i \le W_{\text{comp}}, z_i + H_i \le H_{\text{comp}}$.
   - Bottom support & stacking limit: Elevated items require at least 50% bottom contact area supported by compliant items; total stacked mass must not exceed base item `maxStackWeightKg`.
   - Statutory hazard segregation: IMDG/CMV forbidden co-loading and minimum distance separation.

### Physical Parameter Gaps & Missing Data
- **Vehicle Tare / Chassis Weight**: Not provided in `CompartmentDto`.
- **Axle Positions & Count**: Steer axle, drive tandem, trailer bogie positions and wheelbase distances are not defined by the project contracts.
- **Gross Axle Weight Ratings (GAWR)**: Steer/drive/trailer maximum allowable axle weights are not defined.
- **Fifth Wheel / Kingpin Offset**: Not defined.
- **Regulatory Limit Fallacy**: Because vehicle chassis parameters and axle configurations are absent, inventing arbitrary regulatory axle loads or stability limits would violate safety integrity.
- **Engineering Decision**:
  - The calculated Centre of Gravity is computed with mathematical rigor.
  - Optional authoritative CoG envelope bounding limits (`minCogXMm`, `maxCogXMm`, `minCogYMm`, `maxCogYMm`, `minCogZMm`, `maxCogZMm`) are supported in `CompartmentDto`.
  - When limits are configured, layouts violating the envelope are rejected (`OUT_OF_BOUNDS`).
  - When limits are absent, the calculated CoG is reported informatively as `NOT_CONFIGURED`, explicitly declaring that physical load-balance feasibility has not been established. Misleading claims of axle-weight compliance are eliminated.

---

## 2. Centre of Gravity (CoG) Mathematical Specification

For handling units $i = 1, \dots, n$ with placed masses $m_i \ge 0$ and centroid coordinates $(x_i, y_i, z_i)$:

$$\text{CoG}_x = \frac{\sum_{i=1}^n (m_i \cdot x_i)}{\sum_{i=1}^n m_i}, \quad \text{CoG}_y = \frac{\sum_{i=1}^n (m_i \cdot y_i)}{\sum_{i=1}^n m_i}, \quad \text{CoG}_z = \frac{\sum_{i=1}^n (m_i \cdot z_i)}{\sum_{i=1}^n m_i}$$

### Safeguards & Edge Cases
1. **Zero Total Mass / Empty Layout**:
   - If $\sum m_i = 0$ or $n = 0$: Geometric reference defaults to floor midpoint $(\frac{L_{\text{comp}}}{2}, \frac{W_{\text{comp}}}{2}, 0.0)$, preventing division by zero.
   - Evaluated as `NOT_CONFIGURED` without emitting `NaN` or `Infinity`.
2. **Invalid Mass / Non-Finite Geometry**:
   - Packages with $m_i < 0$, `NaN`, or $\pm\infty$, or non-finite placement coordinates are rejected with `INVALID_DATA` and critical validation findings (`INVALID_MASS_DATA`, `NON_FINITE_COORDINATES`).
3. **Precision & Rounding**:
   - Floating-point calculations maintain full double-precision in validator checks; boundary checks do not round out-of-bounds positions into acceptance.

---

## 3. Authoritative Contract & Model Updates

1. **`CompartmentDto`**:
   - Added optional bounding envelope fields: `minCogXMm`, `maxCogXMm`, `minCogYMm`, `maxCogYMm`, `minCogZMm`, `maxCogZMm`.
2. **`ValidationResultDto`**:
   - Added `cogXMm`, `cogYMm`, `cogZMm`, `totalMassKg`, and `loadBalanceStatus` (`NOT_CONFIGURED`, `BALANCED`, `OUT_OF_BOUNDS`, `INVALID_DATA`).
3. **`IndependentValidator`**:
   - Independently recalculates 3D CoG from item placement AABB centers and masses.
   - Enforces configured boundaries and attaches findings (`COG_OUT_OF_BOUNDS`, `INVALID_MASS_DATA`, `NON_FINITE_COG`).
   - Populates `loadBalanceStatus` without correlated code sharing with the Python solver.
4. **`cpsat_solver.py`**:
   - Added independent CoG calculation to `compute_standard_layout_evaluation`.
   - Extended `validate_candidate_strictly` to enforce CoG boundaries when present in `compartment`.
5. **`PlannerReviewPage.tsx`**:
   - Replaced inaccurate "axle weight balanced" claim.
   - Added dedicated CoG & Load Balance panel displaying calculated coordinates, total placed mass, and explicit feasibility badge (`BALANCED (ENFORCED BOUNDS)`, `OUT OF BOUNDS`, or `NOT CONFIGURED (INFORMATIONAL)`).

---

## 4. Verification Suite & Outcomes

| Test Suite | Scope | Result | Details |
|---|---|---|---|
| **Python Worker Tests** | `workers/optimizer-worker` | **34 / 34 PASSED** (2.32s) | Added tests for hand-calculated CoG, safe zero-mass handling, and boundary enforcement. |
| **Java Validation Service** | `validation-service` | **15 / 15 PASSED** (8.88s) | Tested hand calculations, rotated packages, unequal masses, empty layouts, boundary conditions, and invalid mass data. |
| **Frontend Unit Tests** | `frontend` (Vitest) | **54 / 54 PASSED** (4.33s) | Verified discovery modals, review pages, import modals, and 3D viewers. |
| **Frontend Build** | `frontend` (Vite) | **SUCCESS** (5.42s) | Clean compilation and bundle generation. |
| **Full Maven Reactor** | All 9 modules | **BUILD SUCCESS** (2:19 min) | 20 / 20 integration tests passing across all services. |

---

## 5. Working Tree Preservation

Git status confirms that:
- Uncommitted changes from Phases G.10, G.11A, G.11B, and G.11C remain strictly preserved.
- No files were reset, cleaned, committed, or pushed.
