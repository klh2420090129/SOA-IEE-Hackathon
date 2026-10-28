"""
Versioned Scenario Corpus for 3D Cargo Loading Optimization Benchmarks (Phase F.3).

Features:
- Deterministic fixture generation with fixed random seeds.
- Canonical rule snapshots (statutory/verified vs. synthetic/benchmark).
- Scenarios across Small (4-8 units), Medium (10-15 units), Larger (20-25 units),
  boundary capacity, hazard segregation, partial-assignment, proven-infeasible,
  and timeout/bounded cases.
- Computes SHA-256 canonical hash of input payload to ensure exact parity across runs.
"""

import hashlib
import json
from typing import Dict, Any, List

GENERATOR_VERSION = "1.0.0-f3"

# Canonical Regulatory Rule Snapshot (Seed CMV rules)
STATUTORY_RULE_SNAPSHOT: Dict[str, Any] = {
    "snapshotId": "snap-2026-q4-v1",
    "jurisdiction": "INDIA_CMV_RULES",
    "isSynthetic": False,
    "rules": [
        {
            "ruleId": "rule-cmv-001",
            "ruleCode": "CMV-HAZ-1-3",
            "primaryHazardClass": "1",
            "secondaryHazardClass": "3",
            "segregationType": "SEPARATED_BY_DISTANCE",
            "minDistanceMm": 3000,
            "description": "Explosives and Flammable Liquids must be segregated by at least 3000 mm"
        },
        {
            "ruleId": "rule-cmv-002",
            "ruleCode": "CMV-HAZ-1-2.1",
            "primaryHazardClass": "1",
            "secondaryHazardClass": "2.1",
            "segregationType": "FORBIDDEN",
            "minDistanceMm": 0,
            "description": "Co-loading Explosives and Flammable Gases in the same vehicle is strictly prohibited"
        },
        {
            "ruleId": "rule-cmv-003",
            "ruleCode": "CMV-HAZ-4.3-8",
            "primaryHazardClass": "4.3",
            "secondaryHazardClass": "8",
            "segregationType": "SEPARATED_BY_DISTANCE",
            "minDistanceMm": 2000,
            "description": "Substances dangerous when wet must not be stowed near liquid corrosives"
        }
    ]
}

# Synthetic Rule Snapshot (Explicitly marked non-statutory for academic stress testing)
SYNTHETIC_RULE_SNAPSHOT: Dict[str, Any] = {
    "snapshotId": "snap-synthetic-f3-stress",
    "jurisdiction": "SYNTHETIC_BENCHMARK_ONLY",
    "isSynthetic": True,
    "rules": [
        {
            "ruleId": "syn-rule-001",
            "ruleCode": "SYN-HAZ-5.1-3",
            "primaryHazardClass": "5.1",
            "secondaryHazardClass": "3",
            "segregationType": "FORBIDDEN",
            "minDistanceMm": 0,
            "description": "Synthetic benchmark rule: Oxidizer and Flammable forbidden"
        },
        {
            "ruleId": "syn-rule-002",
            "ruleCode": "SYN-HAZ-6.1-8",
            "primaryHazardClass": "6.1",
            "secondaryHazardClass": "8",
            "segregationType": "SEPARATED_BY_DISTANCE",
            "minDistanceMm": 2500,
            "description": "Synthetic benchmark rule: Toxic and Corrosive separated by 2500mm"
        }
    ]
}

EMPTY_RULE_SNAPSHOT: Dict[str, Any] = {
    "snapshotId": "snap-empty",
    "jurisdiction": "NONE",
    "isSynthetic": False,
    "rules": []
}

def compute_canonical_rule_snapshot_digest(snapshot: Dict[str, Any]) -> str:
    """
    Compute canonical SHA-256 digest identical to Java RuleSnapshotDigestUtils.
    """
    if not snapshot:
        raise ValueError("snapshot cannot be null/empty")
    
    snapshot_id = (snapshot.get("snapshotId") or "").strip()
    jurisdiction = (snapshot.get("jurisdiction") or "").strip().upper()
    
    rules = snapshot.get("rules") or []
    sorted_rules = []
    for r in rules:
        sorted_rules.append({
            "ruleCode": (r.get("ruleCode") or "").strip(),
            "primaryHazardClass": (r.get("primaryHazardClass") or "").strip(),
            "secondaryHazardClass": (r.get("secondaryHazardClass") or "").strip(),
            "segregationType": (r.get("segregationType") or "").strip().upper(),
            "minDistanceMm": int(r.get("minDistanceMm") or 0)
        })
    
    # Sort deterministically
    sorted_rules.sort(key=lambda x: (
        x["ruleCode"],
        x["primaryHazardClass"],
        x["secondaryHazardClass"],
        x["segregationType"],
        x["minDistanceMm"]
    ))
    
    canonical_payload = {
        "snapshotId": snapshot_id,
        "jurisdiction": jurisdiction,
        "rules": sorted_rules
    }
    
    # Order map keys
    serialized = json.dumps(canonical_payload, sort_keys=True, separators=(',', ':'))
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()

def compute_scenario_input_hash(scenario_command: Dict[str, Any]) -> str:
    """
    Compute deterministic SHA-256 hash of the exact input payload (compartment, items, ruleSnapshot).
    Volatile IDs like jobId / attemptId are excluded from the hash for stable cross-run hashing.
    """
    normalized_items = []
    for item in sorted(scenario_command.get("items", []), key=lambda x: x.get("cargoItemId", "")):
        normalized_items.append({
            "cargoItemId": item.get("cargoItemId"),
            "description": item.get("description", ""),
            "lengthMm": item.get("lengthMm"),
            "widthMm": item.get("widthMm"),
            "heightMm": item.get("heightMm"),
            "weightKg": item.get("weightKg"),
            "quantity": item.get("quantity", 1),
            "hazardClass": item.get("hazardClass", "NON_HAZ"),
            "unNumber": item.get("unNumber", "UN0000"),
            "stackable": bool(item.get("stackable")),
            "maxStackWeightKg": float(item.get("maxStackWeightKg", 0.0)),
            "permittedOrientations": sorted(item.get("permittedOrientations") or [])
        })
    
    comp = scenario_command.get("compartment", {})
    normalized_comp = {
        "compartmentId": comp.get("compartmentId"),
        "lengthMm": comp.get("lengthMm"),
        "widthMm": comp.get("widthMm"),
        "heightMm": comp.get("heightMm"),
        "maxPayloadKg": comp.get("maxPayloadKg")
    }
    
    rule_snap = scenario_command.get("ruleSnapshot") or {}
    rule_digest = compute_canonical_rule_snapshot_digest(rule_snap) if rule_snap.get("rules") is not None else ""
    
    payload = {
        "compartment": normalized_comp,
        "items": normalized_items,
        "ruleSnapshotId": rule_snap.get("snapshotId", ""),
        "ruleSnapshotDigest": rule_digest,
        "requireAllPlaced": bool(scenario_command.get("requireAllPlaced", False))
    }
    
    serialized = json.dumps(payload, sort_keys=True, separators=(',', ':'))
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()

class BenchmarkScenario:
    def __init__(self,
                 scenario_id: str,
                 name: str,
                 category: str,
                 instance_size_group: str,
                 expected_property: str,
                 command: Dict[str, Any],
                 seed: int = 42):
        self.scenario_id = scenario_id
        self.name = name
        self.category = category
        self.instance_size_group = instance_size_group
        self.expected_property = expected_property
        self.seed = seed
        self.generator_version = GENERATOR_VERSION
        
        # Attach snapshot digest
        rs = command.get("ruleSnapshot")
        if rs and "rules" in rs:
            rs_digest = compute_canonical_rule_snapshot_digest(rs)
            command["ruleSnapshotDigest"] = rs_digest
            command["ruleSnapshotId"] = rs.get("snapshotId")
        
        self.command = command
        self.input_hash = compute_scenario_input_hash(command)
        
        # Calculate physical handling units count
        self.total_physical_units = sum(item.get("quantity", 1) for item in command.get("items", []))

    def to_dict(self) -> Dict[str, Any]:
        return {
            "scenarioId": self.scenario_id,
            "name": self.name,
            "category": self.category,
            "instanceSizeGroup": self.instance_size_group,
            "totalPhysicalUnits": self.total_physical_units,
            "expectedProperty": self.expected_property,
            "seed": self.seed,
            "generatorVersion": self.generator_version,
            "inputHash": self.input_hash,
            "ruleSnapshotId": self.command.get("ruleSnapshotId"),
            "ruleSnapshotDigest": self.command.get("ruleSnapshotDigest")
        }


def get_scenario_corpus() -> List[BenchmarkScenario]:
    """
    Returns the comprehensive, deterministic 12-scenario corpus across all required categories.
    """
    scenarios: List[BenchmarkScenario] = []

    # 1. SMALL_01: Exact Fit Cube Packing (4 units)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_SMALL_01",
        name="Small: 4 Uniform Units Exact Fit",
        category="DIMENSIONS_ORIENTATION",
        instance_size_group="SMALL",
        expected_property="ALL_PLACED_OPTIMAL",
        seed=101,
        command={
            "jobId": "bench-small-01",
            "attemptId": "att-1",
            "timeLimitSeconds": 5,
            "compartment": {"compartmentId": "comp-2x2", "lengthMm": 2000, "widthMm": 2000, "heightMm": 1000, "maxPayloadKg": 5000.0},
            "ruleSnapshot": EMPTY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "crate-1x1", "description": "1m Crate", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 200.0, "quantity": 4, "stackable": False}
            ]
        }
    ))

    # 2. SMALL_02: Rotatable Yaw 90 Cargo (6 units)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_SMALL_02",
        name="Small: 6 Units Mixed Rotations (Yaw 90)",
        category="DIMENSIONS_ORIENTATION",
        instance_size_group="SMALL",
        expected_property="ALL_PLACED_OPTIMAL",
        seed=102,
        command={
            "jobId": "bench-small-02",
            "attemptId": "att-1",
            "timeLimitSeconds": 5,
            "compartment": {"compartmentId": "comp-narrow-long", "lengthMm": 3600, "widthMm": 2000, "heightMm": 1500, "maxPayloadKg": 6000.0},
            "ruleSnapshot": EMPTY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "rect-box", "description": "1.8x1.0m Box", "lengthMm": 1800, "widthMm": 1000, "heightMm": 1000, "weightKg": 150.0, "quantity": 4, "permittedOrientations": ["DEFAULT", "ROTATE_YAW_90"], "stackable": False},
                {"cargoItemId": "sq-box", "description": "0.9x0.9m Box", "lengthMm": 900, "widthMm": 900, "heightMm": 1000, "weightKg": 100.0, "quantity": 2, "permittedOrientations": ["DEFAULT"], "stackable": False}
            ]
        }
    ))

    # 3. SMALL_03: Stacking and Weight Limits (8 units)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_SMALL_03",
        name="Small: 8 Units Multi-Tier Stacking",
        category="STACKING",
        instance_size_group="SMALL",
        expected_property="ALL_PLACED_OPTIMAL",
        seed=103,
        command={
            "jobId": "bench-small-03",
            "attemptId": "att-1",
            "timeLimitSeconds": 5,
            "compartment": {"compartmentId": "comp-high", "lengthMm": 2000, "widthMm": 2000, "heightMm": 2200, "maxPayloadKg": 8000.0},
            "ruleSnapshot": EMPTY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "base-pallet", "description": "Heavy Stackable Base", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 400.0, "quantity": 4, "stackable": True, "maxStackWeightKg": 1000.0},
                {"cargoItemId": "top-carton", "description": "Lightweight Carton", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 4, "stackable": False}
            ]
        }
    ))

    # 4. MEDIUM_01: Mixed Cargo with Weight Boundary (10 units)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_MED_01",
        name="Medium: 10 Units Capacity Constrained (Payload Bound)",
        category="CAPACITY_CONSTRAINED",
        instance_size_group="MEDIUM",
        expected_property="PARTIAL_PAYLOAD_LIMIT",
        seed=201,
        command={
            "jobId": "bench-med-01",
            "attemptId": "att-1",
            "timeLimitSeconds": 6,
            "compartment": {"compartmentId": "comp-trailer", "lengthMm": 6000, "widthMm": 2400, "heightMm": 2400, "maxPayloadKg": 2100.0},
            "ruleSnapshot": EMPTY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "heavy-unit-A", "description": "300kg Heavy Unit", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 300.0, "quantity": 5, "stackable": True},
                {"cargoItemId": "heavy-unit-B", "description": "250kg Heavy Unit", "lengthMm": 1200, "widthMm": 800, "heightMm": 1000, "weightKg": 250.0, "quantity": 5, "stackable": True}
            ]
        }
    ))

    # 5. MEDIUM_02: Statutory Hazard Segregation (12 units)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_MED_02",
        name="Medium: 12 Units Regulatory Hazard Segregation (CMV)",
        category="HAZARD_SEGREGATION",
        instance_size_group="MEDIUM",
        expected_property="HAZARDS_SEGREGATED_OPTIMAL",
        seed=202,
        command={
            "jobId": "bench-med-02",
            "attemptId": "att-1",
            "timeLimitSeconds": 8,
            "compartment": {"compartmentId": "comp-trailer-long", "lengthMm": 8000, "widthMm": 2400, "heightMm": 2400, "maxPayloadKg": 15000.0},
            "ruleSnapshot": STATUTORY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "chem-c1", "description": "Explosive Detonators", "hazardClass": "1", "unNumber": "UN0029", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 200.0, "quantity": 2, "stackable": False},
                {"cargoItemId": "chem-c3", "description": "Acetone Solvent", "hazardClass": "3", "unNumber": "UN1090", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 200.0, "quantity": 2, "stackable": False},
                {"cargoItemId": "gen-cargo", "description": "General Freight", "hazardClass": "NON_HAZ", "unNumber": "UN0000", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 150.0, "quantity": 8, "stackable": True}
            ]
        }
    ))

    # 6. MEDIUM_03: Partially Assignable Geometric Boundary (15 units)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_MED_03",
        name="Medium: 15 Units Volume Boundary (Partial Placement)",
        category="PARTIALLY_ASSIGNABLE",
        instance_size_group="MEDIUM",
        expected_property="PARTIAL_GEOMETRIC_FIT",
        seed=203,
        command={
            "jobId": "bench-med-03",
            "attemptId": "att-1",
            "timeLimitSeconds": 8,
            "compartment": {"compartmentId": "comp-medium-bin", "lengthMm": 4000, "widthMm": 2000, "heightMm": 2000, "maxPayloadKg": 20000.0},
            "ruleSnapshot": EMPTY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "box-1m", "description": "1m Rigid Crate", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 100.0, "quantity": 15, "stackable": True, "maxStackWeightKg": 1000.0}
            ]
        }
    ))

    # 7. LARGER_01: Scaled Logistics Dispatch (20 units)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_LARGE_01",
        name="Large: 20 Units Multi-Commodity Staged Packing",
        category="SCALED_LOGISTICS",
        instance_size_group="LARGER",
        expected_property="HIGH_PACKING_EFFICIENCY",
        seed=301,
        command={
            "jobId": "bench-large-01",
            "attemptId": "att-1",
            "timeLimitSeconds": 8,
            "compartment": {"compartmentId": "comp-40ft-hc", "lengthMm": 12000, "widthMm": 2400, "heightMm": 2400, "maxPayloadKg": 26000.0},
            "ruleSnapshot": EMPTY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "euro-pallet-a", "description": "Euro Pallet 1.2x0.8", "lengthMm": 1200, "widthMm": 800, "heightMm": 1000, "weightKg": 300.0, "quantity": 10, "stackable": True, "permittedOrientations": ["DEFAULT", "ROTATE_YAW_90"]},
                {"cargoItemId": "iso-pallet-b", "description": "ISO Pallet 1.0x1.0", "lengthMm": 1000, "widthMm": 1000, "heightMm": 900, "weightKg": 250.0, "quantity": 10, "stackable": True, "permittedOrientations": ["DEFAULT"]}
            ]
        }
    ))

    # 8. LARGER_02: 24 Units Scaled Packing (Subject to Practical Time Bound)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_LARGE_02",
        name="Large: 24 Units High-Density Fleet Packing",
        category="SCALED_LOGISTICS",
        instance_size_group="LARGER",
        expected_property="HIGH_DENSITY_PLACEMENT",
        seed=302,
        command={
            "jobId": "bench-large-02",
            "attemptId": "att-1",
            "timeLimitSeconds": 8,
            "compartment": {"compartmentId": "comp-40ft-std", "lengthMm": 12000, "widthMm": 2400, "heightMm": 2400, "maxPayloadKg": 28000.0},
            "ruleSnapshot": EMPTY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "fleet-pallet-1", "description": "1.2x1.0 Pallet", "lengthMm": 1200, "widthMm": 1000, "heightMm": 1100, "weightKg": 350.0, "quantity": 12, "stackable": True, "permittedOrientations": ["DEFAULT", "ROTATE_YAW_90"]},
                {"cargoItemId": "fleet-pallet-2", "description": "1.0x0.8 Pallet", "lengthMm": 1000, "widthMm": 800, "heightMm": 1000, "weightKg": 280.0, "quantity": 12, "stackable": True, "permittedOrientations": ["DEFAULT"]}
            ]
        }
    ))

    # 9. PROVEN_INFEASIBLE_01: Oversized Single Dimension (Strict requireAllPlaced)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_INFEASIBLE_01",
        name="Infeasible: Single Oversized Unit (Model Proven)",
        category="PROVEN_INFEASIBLE",
        instance_size_group="SMALL",
        expected_property="PROVEN_INFEASIBLE_NO_FALLBACK",
        seed=401,
        command={
            "jobId": "bench-infeas-01",
            "attemptId": "att-1",
            "timeLimitSeconds": 3,
            "requireAllPlaced": True,
            "compartment": {"compartmentId": "comp-small-box", "lengthMm": 2000, "widthMm": 2000, "heightMm": 2000, "maxPayloadKg": 5000.0},
            "ruleSnapshot": EMPTY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "oversize-beam", "description": "3m Long Beam", "lengthMm": 3000, "widthMm": 800, "heightMm": 800, "weightKg": 400.0, "quantity": 1, "stackable": False}
            ]
        }
    ))

    # 10. PROVEN_INFEASIBLE_02: Conflicting Forbidden Rules on Co-loading (Strict requireAllPlaced)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_INFEASIBLE_02",
        name="Infeasible: Co-loading Forbidden by Statutory Law (Class 1 & 2.1)",
        category="PROVEN_INFEASIBLE",
        instance_size_group="SMALL",
        expected_property="PROVEN_INFEASIBLE_NO_FALLBACK",
        seed=402,
        command={
            "jobId": "bench-infeas-02",
            "attemptId": "att-1",
            "timeLimitSeconds": 3,
            "requireAllPlaced": True,
            "compartment": {"compartmentId": "comp-trailer", "lengthMm": 6000, "widthMm": 2400, "heightMm": 2400, "maxPayloadKg": 10000.0},
            "ruleSnapshot": STATUTORY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "exp-c1", "description": "Class 1 Explosives", "hazardClass": "1", "unNumber": "UN0029", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 200.0, "quantity": 1, "stackable": False},
                {"cargoItemId": "gas-c21", "description": "Class 2.1 Flammable Gas", "hazardClass": "2.1", "unNumber": "UN1075", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 200.0, "quantity": 1, "stackable": False}
            ]
        }
    ))

    # 11. SYNTHETIC_STRESS_01: Synthetic Stress Multi-Rule Constraints (14 units)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_SYNTH_01",
        name="Synthetic: 14 Units Stress Rules (Academic Non-Statutory)",
        category="SYNTHETIC_STRESS",
        instance_size_group="MEDIUM",
        expected_property="ACADEMIC_SYNTHETIC_EVALUATION",
        seed=501,
        command={
            "jobId": "bench-synth-01",
            "attemptId": "att-1",
            "timeLimitSeconds": 8,
            "compartment": {"compartmentId": "comp-flatbed", "lengthMm": 7000, "widthMm": 2400, "heightMm": 2000, "maxPayloadKg": 12000.0},
            "ruleSnapshot": SYNTHETIC_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "ox-5.1", "description": "Synthetic Oxidizer", "hazardClass": "5.1", "unNumber": "UN1479", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 250.0, "quantity": 2, "stackable": False},
                {"cargoItemId": "flam-3", "description": "Synthetic Solvent", "hazardClass": "3", "unNumber": "UN1993", "lengthMm": 1000, "widthMm": 1000, "heightMm": 1000, "weightKg": 250.0, "quantity": 2, "stackable": False},
                {"cargoItemId": "neutral-box", "description": "Neutral Cargo", "hazardClass": "NON_HAZ", "unNumber": "UN0000", "lengthMm": 1000, "widthMm": 800, "heightMm": 800, "weightKg": 150.0, "quantity": 10, "stackable": True}
            ]
        }
    ))

    # 12. BOUNDED_TIMEOUT_01: Deliberately Constrained Budget Incumbent Preservation (16 units)
    scenarios.append(BenchmarkScenario(
        scenario_id="SCEN_BOUND_01",
        name="Bounded: 16 Units Micro-Budget Graceful Incumbent Preservation",
        category="TIMEOUT_BOUNDED",
        instance_size_group="MEDIUM",
        expected_property="PRESERVES_VALID_INCUMBENT_ON_TIMEOUT",
        seed=601,
        command={
            "jobId": "bench-bound-01",
            "attemptId": "att-1",
            "timeLimitSeconds": 1, # Ultra-tight 1.0s limit to test graceful incumbent preservation
            "compartment": {"compartmentId": "comp-iso-box", "lengthMm": 5000, "widthMm": 2400, "heightMm": 2400, "maxPayloadKg": 15000.0},
            "ruleSnapshot": EMPTY_RULE_SNAPSHOT,
            "items": [
                {"cargoItemId": "crate-tight-a", "description": "Packing Crate A", "lengthMm": 1100, "widthMm": 900, "heightMm": 900, "weightKg": 200.0, "quantity": 8, "stackable": True},
                {"cargoItemId": "crate-tight-b", "description": "Packing Crate B", "lengthMm": 900, "widthMm": 900, "heightMm": 900, "weightKg": 180.0, "quantity": 8, "stackable": True}
            ]
        }
    ))

    return scenarios
