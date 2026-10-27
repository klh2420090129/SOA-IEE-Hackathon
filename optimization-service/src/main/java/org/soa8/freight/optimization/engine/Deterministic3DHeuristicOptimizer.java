package org.soa8.freight.optimization.engine;

import org.soa8.freight.common.model.*;
import org.soa8.freight.common.util.DigestUtils;
import org.soa8.freight.common.util.RuleSnapshotDigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Deterministic 3D Cargo-Packing Heuristic Engine for Milestone 2 Phase B.
 *
 * Implements a deterministic Extreme-Point (EP) / Bottom-Left-Back placement heuristic:
 * - Coordinates: X (length), Y (width), Z (height) in integer millimetres, origin at lower-corner (0,0,0).
 * - Enforces boundary containment: x + L <= comp.L, y + W <= comp.W, z + H <= comp.H.
 * - Enforces non-overlapping pairwise 3D axis-aligned bounding boxes (AABBs).
 * - Evaluates orientations: DEFAULT (0 deg yaw) and ROTATE_YAW_90 (90 deg yaw swap L/W) ONLY IF explicitly permitted.
 * - Bottom support / stacking check: When z > 0, checks if resting on top of a lower item or ground.
 * - Segregation constraints: Evaluates FORBIDDEN and SEPARATED_BY_DISTANCE rules using Euclidean 3D center-to-center distance,
 *   matching IndependentValidator exactly.
 * - Payload capacity check: Cumulative weight must not exceed compartment.maxPayloadKg.
 * - Honest unassigned item accounting: Unplaced units are recorded with stable reason codes.
 * - Never claims OPTIMAL. Reports FEASIBLE when placements are made, or UNKNOWN/INFEASIBLE.
 */
@Service
public class Deterministic3DHeuristicOptimizer {

    private static final Logger log = LoggerFactory.getLogger(Deterministic3DHeuristicOptimizer.class);

    public static final String ALGORITHM_PROVENANCE = "HEURISTIC_EP_3D_V1";
    public static final String ALGORITHM_VERSION = "1.0.0-m2";

    public static final String ORIENTATION_DEFAULT = "DEFAULT";
    public static final String ORIENTATION_ROTATE_YAW_90 = "ROTATE_YAW_90";

    public static class PhysicalItemUnit {
        private final String cargoItemId;
        private final int instanceIndex;
        private final String description;
        private final String hazardClass;
        private final String unNumber;
        private final int lengthMm;
        private final int widthMm;
        private final int heightMm;
        private final double weightKg;
        private final boolean stackable;
        private final double maxStackWeightKg;
        private final List<String> permittedOrientations;

        public PhysicalItemUnit(String cargoItemId, int instanceIndex, String description, String hazardClass,
                                String unNumber, int lengthMm, int widthMm, int heightMm, double weightKg,
                                boolean stackable, double maxStackWeightKg, List<String> permittedOrientations) {
            this.cargoItemId = cargoItemId;
            this.instanceIndex = instanceIndex;
            this.description = description;
            this.hazardClass = hazardClass;
            this.unNumber = unNumber;
            this.lengthMm = lengthMm;
            this.widthMm = widthMm;
            this.heightMm = heightMm;
            this.weightKg = weightKg;
            this.stackable = stackable;
            this.maxStackWeightKg = maxStackWeightKg;
            this.permittedOrientations = permittedOrientations != null ? permittedOrientations : List.of(ORIENTATION_DEFAULT);
        }

        public String getCargoItemId() { return cargoItemId; }
        public int getInstanceIndex() { return instanceIndex; }
        public String getDescription() { return description; }
        public String getHazardClass() { return hazardClass; }
        public String getUnNumber() { return unNumber; }
        public int getLengthMm() { return lengthMm; }
        public int getWidthMm() { return widthMm; }
        public int getHeightMm() { return heightMm; }
        public double getWeightKg() { return weightKg; }
        public boolean isStackable() { return stackable; }
        public double getMaxStackWeightKg() { return maxStackWeightKg; }
        public List<String> getPermittedOrientations() { return permittedOrientations; }
    }

    public static class OrientationDimensions {
        public final String orientationName;
        public final int lengthMm;
        public final int widthMm;
        public final int heightMm;

        public OrientationDimensions(String orientationName, int lengthMm, int widthMm, int heightMm) {
            this.orientationName = orientationName;
            this.lengthMm = lengthMm;
            this.widthMm = widthMm;
            this.heightMm = heightMm;
        }
    }

    public static class ExtremePoint implements Comparable<ExtremePoint> {
        public final int x;
        public final int y;
        public final int z;

        public ExtremePoint(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int compareTo(ExtremePoint o) {
            if (this.z != o.z) return Integer.compare(this.z, o.z);
            if (this.x != o.x) return Integer.compare(this.x, o.x);
            return Integer.compare(this.y, o.y);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExtremePoint that = (ExtremePoint) o;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + "," + z + ")";
        }
    }

    public static class CandidatePlacement {
        public final ExtremePoint point;
        public final OrientationDimensions orientation;
        public final double score;

        public CandidatePlacement(ExtremePoint point, OrientationDimensions orientation, double score) {
            this.point = point;
            this.orientation = orientation;
            this.score = score;
        }
    }

    /**
     * Primary entry point to optimize packing for a declaration into a compartment.
     */
    public CandidateLayoutDto optimize(CompartmentDto compartment, List<CargoItemDto> items,
                                        RuleSnapshotDto ruleSnapshot, String requestedSnapshotId) {
        long startTime = System.currentTimeMillis();

        if (compartment == null) {
            throw new IllegalArgumentException("Compartment must not be null");
        }
        if (compartment.getLengthMm() <= 0 || compartment.getWidthMm() <= 0 || compartment.getHeightMm() <= 0) {
            throw new IllegalArgumentException("Compartment dimensions must be positive integer millimetres");
        }
        if (compartment.getMaxPayloadKg() <= 0) {
            throw new IllegalArgumentException("Compartment maxPayloadKg must be positive");
        }

        // Validate and expand physical units
        List<PhysicalItemUnit> physicalUnits = expandAndValidateUnits(items);

        // Sort physical units deterministically (Decreasing Volume, then Decreasing Weight, then ID, then instance)
        physicalUnits.sort((u1, u2) -> {
            long vol1 = (long) u1.getLengthMm() * u1.getWidthMm() * u1.getHeightMm();
            long vol2 = (long) u2.getLengthMm() * u2.getWidthMm() * u2.getHeightMm();
            int c = Long.compare(vol2, vol1);
            if (c != 0) return c;
            c = Double.compare(u2.getWeightKg(), u1.getWeightKg());
            if (c != 0) return c;
            c = u1.getCargoItemId().compareTo(u2.getCargoItemId());
            if (c != 0) return c;
            return Integer.compare(u1.getInstanceIndex(), u2.getInstanceIndex());
        });

        List<ItemPlacementDto> placedItems = new ArrayList<>();
        List<UnassignedItemDto> unassignedItems = new ArrayList<>();
        TreeSet<ExtremePoint> extremePoints = new TreeSet<>();
        extremePoints.add(new ExtremePoint(0, 0, 0));

        double currentTotalWeightKg = 0.0;
        int candidateEvaluationsCount = 0;

        List<CompatibilityRuleDto> activeRules = (ruleSnapshot != null && ruleSnapshot.getRules() != null)
                ? ruleSnapshot.getRules() : Collections.emptyList();

        for (PhysicalItemUnit unit : physicalUnits) {
            // Check overall compartment weight limit
            if (currentTotalWeightKg + unit.getWeightKg() > compartment.getMaxPayloadKg()) {
                unassignedItems.add(new UnassignedItemDto(
                        unit.getCargoItemId(),
                        unit.getInstanceIndex(),
                        "PAYLOAD_CAPACITY_EXCEEDED",
                        "Placement exceeds compartment maximum payload capacity of " + compartment.getMaxPayloadKg() + " kg"
                ));
                continue;
            }

            // Find all valid candidate placements for this unit across extreme points & permitted orientations
            List<CandidatePlacement> validPlacements = new ArrayList<>();
            String primaryFailureReason = "NO_FIT_GEOMETRY";
            String primaryFailureDesc = "No feasible position found within compartment boundaries";

            List<OrientationDimensions> orientations = getPermittedOrientationDimensions(unit);

            for (ExtremePoint ep : extremePoints) {
                for (OrientationDimensions orient : orientations) {
                    candidateEvaluationsCount++;

                    // 1. Boundary Containment Check
                    if (ep.x + orient.lengthMm > compartment.getLengthMm() ||
                        ep.y + orient.widthMm > compartment.getWidthMm() ||
                        ep.z + orient.heightMm > compartment.getHeightMm()) {
                        continue;
                    }

                    // 2. 3D Pairwise AABB Collision Check
                    if (has3DCollision(ep, orient, placedItems)) {
                        continue;
                    }

                    // 3. Bottom Support & Stacking Check
                    if (!isSupported(ep, orient, placedItems)) {
                        primaryFailureReason = "UNSUPPORTED_STACKING";
                        primaryFailureDesc = "Unit cannot float without bottom support from floor or stackable cargo";
                        continue;
                    }

                    // 4. Stacking Compatibility & Weight Limit Check on supporting items
                    if (!canStackOnSupportingItems(ep, orient, unit, placedItems)) {
                        primaryFailureReason = "STACKING_WEIGHT_OR_UNSTACKABLE";
                        primaryFailureDesc = "Supporting base item is not stackable or max stack weight would be exceeded";
                        continue;
                    }

                    // 5. Segregation Rules Check against already placed items
                    SegregationCheckResult segResult = checkSegregation(ep, orient, unit, placedItems, activeRules);
                    if (!segResult.isAllowed) {
                        primaryFailureReason = segResult.reasonCode;
                        primaryFailureDesc = segResult.description;
                        continue;
                    }

                    // Feasible candidate found! Calculate score
                    // Compactness score: Prioritize low Z, then low X, then low Y, with small penalty for non-default orientation
                    double orientationPenalty = ORIENTATION_DEFAULT.equals(orient.orientationName) ? 0.0 : 10.0;
                    double score = (ep.z * 1000000.0) + (ep.x * 1000.0) + ep.y + orientationPenalty;
                    validPlacements.add(new CandidatePlacement(ep, orient, score));
                }
            }

            if (validPlacements.isEmpty()) {
                unassignedItems.add(new UnassignedItemDto(
                        unit.getCargoItemId(),
                        unit.getInstanceIndex(),
                        primaryFailureReason,
                        primaryFailureDesc
                ));
            } else {
                // Select the best candidate (lowest score) deterministically
                validPlacements.sort(Comparator.comparingDouble(cp -> cp.score));
                CandidatePlacement best = validPlacements.get(0);

                String placementId = "pl-" + unit.getCargoItemId() + "-" + unit.getInstanceIndex() + "-" + (placedItems.size() + 1);
                ItemPlacementDto placement = new ItemPlacementDto(
                        placementId,
                        unit.getCargoItemId(),
                        unit.getInstanceIndex(),
                        unit.getHazardClass(),
                        unit.getUnNumber(),
                        best.point.x,
                        best.point.y,
                        best.point.z,
                        best.orientation.lengthMm,
                        best.orientation.widthMm,
                        best.orientation.heightMm,
                        unit.getWeightKg(),
                        best.orientation.orientationName,
                        unit.isStackable(),
                        unit.getMaxStackWeightKg()
                );
                placedItems.add(placement);
                currentTotalWeightKg += unit.getWeightKg();

                // Generate new candidate Extreme Points around the 3 faces of the new placement (plus segregation offsets)
                addNewExtremePoints(best.point, best.orientation, extremePoints, compartment, placedItems, activeRules);
            }
        }

        long executionDurationMs = System.currentTimeMillis() - startTime;

        // Determine outcome status honestly
        SolverStatus outcomeStatus;
        if (placedItems.isEmpty() && !physicalUnits.isEmpty()) {
            // A greedy heuristic cannot prove mathematical infeasibility merely because it placed 0 units.
            // UNKNOWN accurately reflects that no feasible candidate was found without false optimality or infeasibility claims.
            outcomeStatus = SolverStatus.UNKNOWN;
        } else {
            // FEASIBLE represents that a candidate layout was produced (either partial or complete).
            // A heuristic NEVER emits OPTIMAL.
            outcomeStatus = SolverStatus.FEASIBLE;
        }

        String layoutId = UUID.randomUUID().toString();
        String contentDigest = org.soa8.freight.common.util.CandidateDigestUtils.computeCanonicalDigest(placedItems);

        String canonicalRuleSnapshotDigest = (ruleSnapshot != null)
                ? RuleSnapshotDigestUtils.computeCanonicalDigest(ruleSnapshot)
                : null;

        String snapshotIdToRecord = (requestedSnapshotId != null && !requestedSnapshotId.isBlank())
                ? requestedSnapshotId
                : (ruleSnapshot != null ? ruleSnapshot.getSnapshotId() : "snap-2026-q4-v1");

        String diagnostics = String.format(
                "{\"engine\":\"%s\",\"version\":\"%s\",\"unitsTotal\":%d,\"unitsPlaced\":%d,\"unitsUnassigned\":%d,\"evaluations\":%d,\"ruleSnapshotDigest\":\"%s\"}",
                ALGORITHM_PROVENANCE, ALGORITHM_VERSION, physicalUnits.size(), placedItems.size(),
                unassignedItems.size(), candidateEvaluationsCount,
                canonicalRuleSnapshotDigest != null ? canonicalRuleSnapshotDigest : "N/A"
        );

        return new CandidateLayoutDto(
                layoutId,
                1,
                compartment.getCompartmentId(),
                contentDigest,
                outcomeStatus.name(),
                ALGORITHM_PROVENANCE,
                snapshotIdToRecord,
                executionDurationMs,
                placedItems,
                unassignedItems,
                diagnostics
        );
    }

    private List<PhysicalItemUnit> expandAndValidateUnits(List<CargoItemDto> items) {
        List<PhysicalItemUnit> units = new ArrayList<>();
        if (items == null) return units;

        Set<String> seenIds = new HashSet<>();

        for (CargoItemDto item : items) {
            if (item.getCargoItemId() == null || item.getCargoItemId().isBlank()) {
                throw new IllegalArgumentException("Cargo item ID must not be blank");
            }
            if (seenIds.contains(item.getCargoItemId())) {
                throw new IllegalArgumentException("Duplicate cargo item ID detected in declaration: " + item.getCargoItemId());
            }
            seenIds.add(item.getCargoItemId());

            if (item.getLengthMm() <= 0 || item.getWidthMm() <= 0 || item.getHeightMm() <= 0) {
                throw new IllegalArgumentException("Cargo item dimensions must be positive integers: " + item.getCargoItemId());
            }
            if (item.getWeightKg() < 0) {
                throw new IllegalArgumentException("Cargo item weight cannot be negative: " + item.getCargoItemId());
            }
            if (item.getQuantity() < 0) {
                throw new IllegalArgumentException("Cargo item quantity cannot be negative: " + item.getCargoItemId());
            }

            for (int i = 0; i < item.getQuantity(); i++) {
                units.add(new PhysicalItemUnit(
                        item.getCargoItemId(),
                        i,
                        item.getDescription(),
                        item.getHazardClass() != null ? item.getHazardClass() : "NON_HAZ",
                        item.getUnNumber() != null ? item.getUnNumber() : "UN0000",
                        item.getLengthMm(),
                        item.getWidthMm(),
                        item.getHeightMm(),
                        item.getWeightKg(),
                        item.isStackable(),
                        item.getMaxStackWeightKg(),
                        item.getPermittedOrientations()
                ));
            }
        }
        return units;
    }

    private List<OrientationDimensions> getPermittedOrientationDimensions(PhysicalItemUnit unit) {
        List<OrientationDimensions> list = new ArrayList<>();
        // Default orientation is always length x width x height
        list.add(new OrientationDimensions(ORIENTATION_DEFAULT, unit.getLengthMm(), unit.getWidthMm(), unit.getHeightMm()));

        // Check if yaw 90 rotation is permitted
        if (unit.getPermittedOrientations() != null &&
            unit.getPermittedOrientations().stream().anyMatch(o -> ORIENTATION_ROTATE_YAW_90.equalsIgnoreCase(o) || "YAW_90".equalsIgnoreCase(o))) {
            // Swap length and width, height unchanged
            if (unit.getLengthMm() != unit.getWidthMm()) {
                list.add(new OrientationDimensions(ORIENTATION_ROTATE_YAW_90, unit.getWidthMm(), unit.getLengthMm(), unit.getHeightMm()));
            }
        }
        return list;
    }

    private boolean has3DCollision(ExtremePoint ep, OrientationDimensions orient, List<ItemPlacementDto> placedItems) {
        int x1 = ep.x;
        int y1 = ep.y;
        int z1 = ep.z;
        int x2 = ep.x + orient.lengthMm;
        int y2 = ep.y + orient.widthMm;
        int z2 = ep.z + orient.heightMm;

        for (ItemPlacementDto other : placedItems) {
            int ox1 = other.getXMm();
            int oy1 = other.getYMm();
            int oz1 = other.getZMm();
            int ox2 = other.getXMm() + other.getLengthMm();
            int oy2 = other.getYMm() + other.getWidthMm();
            int oz2 = other.getZMm() + other.getHeightMm();

            // Strict interior overlap test: touching faces are permitted
            boolean overlapX = x1 < ox2 && x2 > ox1;
            boolean overlapY = y1 < oy2 && y2 > oy1;
            boolean overlapZ = z1 < oz2 && z2 > oz1;

            if (overlapX && overlapY && overlapZ) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupported(ExtremePoint ep, OrientationDimensions orient, List<ItemPlacementDto> placedItems) {
        if (ep.z == 0) {
            return true; // Supported by compartment floor
        }

        // For elevated items, require that at least 50% of the base area is directly supported by item(s) directly underneath
        int x1 = ep.x;
        int y1 = ep.y;
        int x2 = ep.x + orient.lengthMm;
        int y2 = ep.y + orient.widthMm;
        long totalBaseArea = (long) orient.lengthMm * orient.widthMm;
        long supportedArea = 0;

        for (ItemPlacementDto lower : placedItems) {
            // Must be directly beneath: lower top face == ep.z
            int lowerTop = lower.getZMm() + lower.getHeightMm();
            if (lowerTop == ep.z) {
                int lx1 = lower.getXMm();
                int ly1 = lower.getYMm();
                int lx2 = lower.getXMm() + lower.getLengthMm();
                int ly2 = lower.getYMm() + lower.getWidthMm();

                int interX = Math.max(0, Math.min(x2, lx2) - Math.max(x1, lx1));
                int interY = Math.max(0, Math.min(y2, ly2) - Math.max(y1, ly1));
                supportedArea += ((long) interX * interY);
            }
        }

        return supportedArea >= (totalBaseArea * 0.5);
    }

    private boolean canStackOnSupportingItems(ExtremePoint ep, OrientationDimensions orient,
                                             PhysicalItemUnit unit, List<ItemPlacementDto> placedItems) {
        if (ep.z == 0) {
            return true;
        }

        int x1 = ep.x;
        int y1 = ep.y;
        int x2 = ep.x + orient.lengthMm;
        int y2 = ep.y + orient.widthMm;

        for (ItemPlacementDto lower : placedItems) {
            int lowerTop = lower.getZMm() + lower.getHeightMm();
            if (lowerTop == ep.z) {
                int lx1 = lower.getXMm();
                int ly1 = lower.getYMm();
                int lx2 = lower.getXMm() + lower.getLengthMm();
                int ly2 = lower.getYMm() + lower.getWidthMm();

                boolean overlapX = x1 < lx2 && x2 > lx1;
                boolean overlapY = y1 < ly2 && y2 > ly1;

                if (overlapX && overlapY) {
                    // Check if supporting item is stackable
                    if (!lower.isStackable()) {
                        return false; // Lower item is non-stackable
                    }

                    // Check if adding unit's weight exceeds lower's maxStackWeightKg
                    if (lower.getMaxStackWeightKg() != null && lower.getMaxStackWeightKg() > 0) {
                        double currentStackedWeight = computeCurrentStackedWeightOn(lower, placedItems);
                        if (currentStackedWeight + unit.getWeightKg() > lower.getMaxStackWeightKg()) {
                            return false; // Exceeds lower's maximum permitted stack weight
                        }
                    }
                }
            }
        }
        return true;
    }

    private double computeCurrentStackedWeightOn(ItemPlacementDto base, List<ItemPlacementDto> placedItems) {
        double weight = 0.0;
        int bx1 = base.getXMm();
        int by1 = base.getYMm();
        int bx2 = base.getXMm() + base.getLengthMm();
        int by2 = base.getYMm() + base.getWidthMm();
        int bTop = base.getZMm() + base.getHeightMm();

        for (ItemPlacementDto item : placedItems) {
            if (item.getZMm() >= bTop) {
                int ix1 = item.getXMm();
                int iy1 = item.getYMm();
                int ix2 = item.getXMm() + item.getLengthMm();
                int iy2 = item.getYMm() + item.getWidthMm();

                if (ix1 < bx2 && ix2 > bx1 && iy1 < by2 && iy2 > by1) {
                    weight += item.getWeightKg();
                }
            }
        }
        return weight;
    }

    private static class SegregationCheckResult {
        public final boolean isAllowed;
        public final String reasonCode;
        public final String description;

        public SegregationCheckResult(boolean isAllowed, String reasonCode, String description) {
            this.isAllowed = isAllowed;
            this.reasonCode = reasonCode;
            this.description = description;
        }

        public static SegregationCheckResult ok() {
            return new SegregationCheckResult(true, null, null);
        }

        public static SegregationCheckResult violated(String reasonCode, String description) {
            return new SegregationCheckResult(false, reasonCode, description);
        }
    }

    private SegregationCheckResult checkSegregation(ExtremePoint ep, OrientationDimensions orient,
                                                    PhysicalItemUnit unit, List<ItemPlacementDto> placedItems,
                                                    List<CompatibilityRuleDto> rules) {
        if (rules == null || rules.isEmpty()) {
            return SegregationCheckResult.ok();
        }

        double cxa = ep.x + (orient.lengthMm / 2.0);
        double cya = ep.y + (orient.widthMm / 2.0);
        double cza = ep.z + (orient.heightMm / 2.0);

        for (ItemPlacementDto placed : placedItems) {
            double cxb = placed.getXMm() + (placed.getLengthMm() / 2.0);
            double cyb = placed.getYMm() + (placed.getWidthMm() / 2.0);
            double czb = placed.getZMm() + (placed.getHeightMm() / 2.0);

            for (CompatibilityRuleDto rule : rules) {
                boolean match = (rule.getPrimaryHazardClass().equals(unit.getHazardClass()) && rule.getSecondaryHazardClass().equals(placed.getHazardClass())) ||
                                (rule.getPrimaryHazardClass().equals(placed.getHazardClass()) && rule.getSecondaryHazardClass().equals(unit.getHazardClass()));

                if (match) {
                    if ("FORBIDDEN".equalsIgnoreCase(rule.getSegregationType())) {
                        return SegregationCheckResult.violated(
                                "HAZARD_COLOAD_FORBIDDEN",
                                "Co-loading Class " + unit.getHazardClass() + " with Class " + placed.getHazardClass() +
                                " is forbidden by rule " + rule.getRuleCode()
                        );
                    } else if ("SEPARATED_BY_DISTANCE".equalsIgnoreCase(rule.getSegregationType())) {
                        double euclideanDist = Math.sqrt(Math.pow(cxa - cxb, 2) + Math.pow(cya - cyb, 2) + Math.pow(cza - czb, 2));
                        if (euclideanDist < rule.getMinDistanceMm()) {
                            return SegregationCheckResult.violated(
                                    "HAZARD_SEGREGATION_VIOLATION",
                                    "Class " + unit.getHazardClass() + " and Class " + placed.getHazardClass() +
                                    " center distance (" + (int) euclideanDist + " mm) is below required " + rule.getMinDistanceMm() +
                                    " mm by rule " + rule.getRuleCode()
                            );
                        }
                    }
                }
            }
        }

        return SegregationCheckResult.ok();
    }

    private void addNewExtremePoints(ExtremePoint p, OrientationDimensions orient,
                                     TreeSet<ExtremePoint> extremePoints, CompartmentDto comp,
                                     List<ItemPlacementDto> placedItems, List<CompatibilityRuleDto> rules) {
        // Standard extreme points around the 3 placement faces
        // Point on right face: (x + L, y, z)
        addPointIfValid(new ExtremePoint(p.x + orient.lengthMm, p.y, p.z), extremePoints, comp, placedItems);

        // Point on front face: (x, y + W, z)
        addPointIfValid(new ExtremePoint(p.x, p.y + orient.widthMm, p.z), extremePoints, comp, placedItems);

        // Point on top face: (x, y, z + H)
        addPointIfValid(new ExtremePoint(p.x, p.y, p.z + orient.heightMm), extremePoints, comp, placedItems);

        // For distance-segregated hazard rules, also add candidate points offset along X and Y by minDistanceMm
        if (rules != null) {
            for (CompatibilityRuleDto rule : rules) {
                if ("SEPARATED_BY_DISTANCE".equalsIgnoreCase(rule.getSegregationType()) && rule.getMinDistanceMm() > 0) {
                    addPointIfValid(new ExtremePoint(p.x + orient.lengthMm + rule.getMinDistanceMm(), p.y, p.z), extremePoints, comp, placedItems);
                    addPointIfValid(new ExtremePoint(p.x, p.y + orient.widthMm + rule.getMinDistanceMm(), p.z), extremePoints, comp, placedItems);
                }
            }
        }
    }

    private void addPointIfValid(ExtremePoint ep, TreeSet<ExtremePoint> extremePoints,
                                 CompartmentDto comp, List<ItemPlacementDto> placedItems) {
        if (ep.x >= comp.getLengthMm() || ep.y >= comp.getWidthMm() || ep.z >= comp.getHeightMm()) {
            return;
        }

        // Avoid adding points that lie strictly inside existing placed items
        for (ItemPlacementDto item : placedItems) {
            if (ep.x >= item.getXMm() && ep.x < item.getXMm() + item.getLengthMm() &&
                ep.y >= item.getYMm() && ep.y < item.getYMm() + item.getWidthMm() &&
                ep.z >= item.getZMm() && ep.z < item.getZMm() + item.getHeightMm()) {
                return;
            }
        }

        extremePoints.add(ep);
    }
}
