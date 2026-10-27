# Optimization & Validation Architecture

## 1. Optimizer Honesty & Milestone Progression

### Milestone 1: Controlled Deterministic Stub
- **Status:** Milestone 1 implementation.
- **Label / Provenance:** `CONTROLLED_STUB` (explicitly returned in job results and API metadata).
- **Behavior:**
  - Consumes exact versioned cargo declaration and compartment definitions.
  - Returns a predictable, deterministic coordinate placement for controlled fixtures.
  - **No Optimization Claims:** The stub does not execute mathematical programming or claim optimality. Its sole purpose is proving the end-to-end distributed workflow, messaging, and approval lifecycle.
- **Honesty Rule:** Never represented as a real 3D optimizer in UI, tests, or documentation.

### Milestone 2: Real Optimization Engine
- **Engine:** Python worker integrating Google OR-Tools CP-SAT and 3D heuristic packing algorithms.
- **Objective:** Lexicographic:
  1. Maximize safely assigned cargo volume and quantity.
  2. Minimize number of vehicle compartments utilized.
  3. Minimize handling, re-packing, and axle-imbalance penalties.
- **Status Codes:** `OPTIMAL`, `FEASIBLE`, `INFEASIBLE`, `UNKNOWN`, `TIMEOUT`.
- **Provenance:** `REAL_OPTIMIZER`.

---

## 2. Independent Validation Architecture

The **Validation Service** operates independently of the Optimization Service. It evaluates candidates through an uncompromising zero-trust policy.

### Validation Checks:
1. **Geometric Non-Overlap (3D AABB):**
   $$\forall i \neq j, \quad [x_i, x_i + l_i] \cap [x_j, x_j + l_j] = \emptyset \quad \lor \quad [y_i, y_i + w_i] \cap [y_j, y_j + w_j] = \emptyset \quad \lor \quad [z_i, z_i + h_i] \cap [z_j, z_j + h_j] = \emptyset$$
2. **Boundary Containment:**
   $$x_i \ge 0, \quad x_i + l_i \le L_{\text{compartment}}$$
   $$y_i \ge 0, \quad y_i + w_i \le W_{\text{compartment}}$$
   $$z_i \ge 0, \quad z_i + h_i \le H_{\text{compartment}}$$
3. **Payload & Volume Limits:**
   $$\sum_{i} \text{mass}_i \le \text{MaxPayload}_{\text{compartment}}$$
   $$\sum_{i} (l_i \times w_i \times h_i) \le \text{Volume}_{\text{compartment}}$$
4. **Orientation Validity:**
   - Placements must adhere to permitted 90-degree axis rotations (e.g., this-way-up).
5. **Hazard Segregation:**
   - Pairwise evaluation of hazard classes (e.g., Explosives Class 1 vs Flammables Class 3) against the immutable published rule snapshot. Segregation violations immediately fail the candidate.
