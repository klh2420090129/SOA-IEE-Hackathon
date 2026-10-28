package org.soa8.freight.validation.engine;

import org.soa8.freight.common.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IndependentValidator {

    public static final String VALIDATOR_VERSION = "1.0.0-m1";

    public ValidationResultDto validate(CandidateLayoutDto layout, CompartmentDto compartment, RuleSnapshotDto ruleSnapshot) {
        List<ValidationFindingDto> findings = new ArrayList<>();
        List<ItemPlacementDto> placements = layout.getPlacements() != null ? layout.getPlacements() : List.of();

        // 1. Boundary check & Total Weight Check
        double totalWeight = 0;
        for (ItemPlacementDto item : placements) {
            totalWeight += item.getWeightKg();

            if (item.getXMm() < 0 || item.getYMm() < 0 || item.getZMm() < 0) {
                findings.add(new ValidationFindingDto(
                        "CRITICAL", "NEGATIVE_COORDINATES",
                        "Item " + item.getCargoItemId() + " has negative coordinates",
                        List.of(item.getCargoItemId())
                ));
            }

            if (item.getXMm() + item.getLengthMm() > compartment.getLengthMm() ||
                item.getYMm() + item.getWidthMm() > compartment.getWidthMm() ||
                item.getZMm() + item.getHeightMm() > compartment.getHeightMm()) {
                findings.add(new ValidationFindingDto(
                        "CRITICAL", "OUT_OF_BOUNDS",
                        "Item " + item.getCargoItemId() + " extends beyond compartment boundaries",
                        List.of(item.getCargoItemId())
                ));
            }
        }

        if (totalWeight > compartment.getMaxPayloadKg()) {
            findings.add(new ValidationFindingDto(
                    "CRITICAL", "CAPACITY_EXCEEDED",
                    "Total payload mass (" + totalWeight + " kg) exceeds max compartment limit (" + compartment.getMaxPayloadKg() + " kg)",
                    List.of()
            ));
        }

        // 2. Pairwise 3D AABB non-overlap check
        for (int i = 0; i < placements.size(); i++) {
            ItemPlacementDto a = placements.get(i);
            for (int j = i + 1; j < placements.size(); j++) {
                ItemPlacementDto b = placements.get(j);

                boolean overlapX = a.getXMm() < (b.getXMm() + b.getLengthMm()) && (a.getXMm() + a.getLengthMm()) > b.getXMm();
                boolean overlapY = a.getYMm() < (b.getYMm() + b.getWidthMm()) && (a.getYMm() + a.getWidthMm()) > b.getYMm();
                boolean overlapZ = a.getZMm() < (b.getZMm() + b.getHeightMm()) && (a.getZMm() + a.getHeightMm()) > b.getZMm();

                if (overlapX && overlapY && overlapZ) {
                    findings.add(new ValidationFindingDto(
                            "CRITICAL", "OVERLAP_DETECTED",
                            "Item " + a.getCargoItemId() + " overlaps with item " + b.getCargoItemId(),
                            List.of(a.getCargoItemId(), b.getCargoItemId())
                    ));
                }
            }
        }

        // 3. Hazard Segregation Rule Check
        if (ruleSnapshot != null && ruleSnapshot.getRules() != null) {
            for (CompatibilityRuleDto rule : ruleSnapshot.getRules()) {
                for (int i = 0; i < placements.size(); i++) {
                    ItemPlacementDto a = placements.get(i);
                    for (int j = i + 1; j < placements.size(); j++) {
                        ItemPlacementDto b = placements.get(j);

                        boolean matchPair = (rule.getPrimaryHazardClass().equals(a.getHazardClass()) && rule.getSecondaryHazardClass().equals(b.getHazardClass())) ||
                                           (rule.getPrimaryHazardClass().equals(b.getHazardClass()) && rule.getSecondaryHazardClass().equals(a.getHazardClass()));

                        if (matchPair) {
                            if ("FORBIDDEN".equalsIgnoreCase(rule.getSegregationType())) {
                                findings.add(new ValidationFindingDto(
                                        "CRITICAL", "HAZARD_COLOAD_FORBIDDEN",
                                        "Co-loading Class " + a.getHazardClass() + " with Class " + b.getHazardClass() + " is strictly forbidden by rule " + rule.getRuleCode(),
                                        List.of(a.getCargoItemId(), b.getCargoItemId())
                                ));
                            } else if ("SEPARATED_BY_DISTANCE".equalsIgnoreCase(rule.getSegregationType())) {
                                double distance = calculateEuclideanDistance(a, b);
                                if (distance < rule.getMinDistanceMm()) {
                                    findings.add(new ValidationFindingDto(
                                            "CRITICAL", "HAZARD_SEGREGATION_VIOLATION",
                                            "Class " + a.getHazardClass() + " and Class " + b.getHazardClass() + " distance (" + (int) distance + " mm) is less than required " + rule.getMinDistanceMm() + " mm by rule " + rule.getRuleCode(),
                                            List.of(a.getCargoItemId(), b.getCargoItemId())
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Stacking and Bottom Support Check
        for (ItemPlacementDto item : placements) {
            if (item.getZMm() == 0) {
                // Ground support: directly resting on compartment floor
                continue;
            }

            // Must have qualifying support beneath
            int x1 = item.getXMm();
            int y1 = item.getYMm();
            int x2 = item.getXMm() + item.getLengthMm();
            int y2 = item.getYMm() + item.getWidthMm();
            long baseArea = (long) item.getLengthMm() * item.getWidthMm();
            long supportedArea = 0;
            List<ItemPlacementDto> supportingItems = new ArrayList<>();

            for (ItemPlacementDto lower : placements) {
                if (lower.equals(item)) continue;
                int lowerTop = lower.getZMm() + lower.getHeightMm();
                if (lowerTop == item.getZMm()) {
                    int lx1 = lower.getXMm();
                    int ly1 = lower.getYMm();
                    int lx2 = lower.getXMm() + lower.getLengthMm();
                    int ly2 = lower.getYMm() + lower.getWidthMm();

                    int interX = Math.max(0, Math.min(x2, lx2) - Math.max(x1, lx1));
                    int interY = Math.max(0, Math.min(y2, ly2) - Math.max(y1, ly1));
                    if (interX > 0 && interY > 0) {
                        supportedArea += ((long) interX * interY);
                        supportingItems.add(lower);
                    }
                }
            }

            // Stacking support policy: at least 50% bottom area must be supported
            if (supportingItems.isEmpty()) {
                findings.add(new ValidationFindingDto(
                        "CRITICAL", "UNSUPPORTED_CARGO",
                        "Item " + item.getCargoItemId() + " at elevation Z=" + item.getZMm() + " has zero bottom support",
                        List.of(item.getCargoItemId())
                ));
            } else if (supportedArea < (baseArea * 0.5)) {
                findings.add(new ValidationFindingDto(
                        "CRITICAL", "INSUFFICIENT_SUPPORT",
                        "Item " + item.getCargoItemId() + " has excessive overhang (supported area " +
                        (supportedArea * 100 / baseArea) + "% is below required 50%)",
                        List.of(item.getCargoItemId())
                ));
            }

            // Verify supporting items allow stacking and are not overloaded
            for (ItemPlacementDto supporting : supportingItems) {
                if (supporting.getStackable() == null) {
                    findings.add(new ValidationFindingDto(
                            "CRITICAL", "UNVERIFIED_STACK_CAPABILITY",
                            "Supporting item " + supporting.getCargoItemId() + " has unknown/missing stacking capability but carries upper item " + item.getCargoItemId(),
                            List.of(supporting.getCargoItemId(), item.getCargoItemId())
                    ));
                } else if (!supporting.isStackable()) {
                    findings.add(new ValidationFindingDto(
                            "CRITICAL", "NON_STACKABLE_LOAD_BEARING",
                            "Supporting item " + supporting.getCargoItemId() + " is non-stackable but carries upper item " + item.getCargoItemId(),
                            List.of(supporting.getCargoItemId(), item.getCargoItemId())
                    ));
                }
            }
        }

        // Verify maxStackWeightKg for each base item
        for (ItemPlacementDto base : placements) {
            if (base.getMaxStackWeightKg() != null && base.getMaxStackWeightKg() > 0) {
                double totalStackedWeight = 0;
                int bx1 = base.getXMm();
                int by1 = base.getYMm();
                int bx2 = base.getXMm() + base.getLengthMm();
                int by2 = base.getYMm() + base.getWidthMm();
                int bTop = base.getZMm() + base.getHeightMm();

                for (ItemPlacementDto upper : placements) {
                    if (upper.equals(base)) continue;
                    if (upper.getZMm() >= bTop) {
                        int ux1 = upper.getXMm();
                        int uy1 = upper.getYMm();
                        int ux2 = upper.getXMm() + upper.getLengthMm();
                        int uy2 = upper.getYMm() + upper.getWidthMm();

                        if (ux1 < bx2 && ux2 > bx1 && uy1 < by2 && uy2 > by1) {
                            totalStackedWeight += upper.getWeightKg();
                        }
                    }
                }

                if (totalStackedWeight > base.getMaxStackWeightKg()) {
                    findings.add(new ValidationFindingDto(
                            "CRITICAL", "STACK_WEIGHT_LIMIT_EXCEEDED",
                            "Total weight stacked on item " + base.getCargoItemId() + " (" + totalStackedWeight +
                            " kg) exceeds maximum permitted stack weight (" + base.getMaxStackWeightKg() + " kg)",
                            List.of(base.getCargoItemId())
                    ));
                }
            }
        }

        // 5. Independent Centre of Gravity (CoG) and Load-Balance Feasibility
        double sumMass = 0.0;
        double sumMassX = 0.0;
        double sumMassY = 0.0;
        double sumMassZ = 0.0;
        boolean hasInvalidMassOrGeometry = false;

        for (ItemPlacementDto item : placements) {
            double mass = item.getWeightKg();
            if (Double.isNaN(mass) || Double.isInfinite(mass) || mass < 0.0) {
                findings.add(new ValidationFindingDto(
                        "CRITICAL", "INVALID_MASS_DATA",
                        "Item " + item.getCargoItemId() + " has invalid mass: " + mass,
                        List.of(item.getCargoItemId())
                ));
                hasInvalidMassOrGeometry = true;
                continue;
            }

            if (Double.isNaN(item.getXMm()) || Double.isNaN(item.getYMm()) || Double.isNaN(item.getZMm()) ||
                Double.isNaN(item.getLengthMm()) || Double.isNaN(item.getWidthMm()) || Double.isNaN(item.getHeightMm())) {
                findings.add(new ValidationFindingDto(
                        "CRITICAL", "NON_FINITE_COORDINATES",
                        "Item " + item.getCargoItemId() + " has non-finite geometry or coordinates",
                        List.of(item.getCargoItemId())
                ));
                hasInvalidMassOrGeometry = true;
                continue;
            }

            // Package centre point accounting for placed geometry and orientation:
            // x_i = x + L/2.0, y_i = y + W/2.0, z_i = z + H/2.0
            double cx = item.getXMm() + (item.getLengthMm() / 2.0);
            double cy = item.getYMm() + (item.getWidthMm() / 2.0);
            double cz = item.getZMm() + (item.getHeightMm() / 2.0);

            sumMass += mass;
            sumMassX += (mass * cx);
            sumMassY += (mass * cy);
            sumMassZ += (mass * cz);
        }

        Double cogX = null;
        Double cogY = null;
        Double cogZ = null;
        String loadBalanceStatus;

        if (hasInvalidMassOrGeometry) {
            loadBalanceStatus = "INVALID_DATA";
        } else if (placements.isEmpty() || sumMass == 0.0) {
            // Safe handling for empty layout or zero total mass without emitting NaN or Infinity:
            // Geometric reference defaults to floor center
            cogX = compartment != null ? compartment.getLengthMm() / 2.0 : 0.0;
            cogY = compartment != null ? compartment.getWidthMm() / 2.0 : 0.0;
            cogZ = 0.0;
            loadBalanceStatus = "NOT_CONFIGURED";
        } else {
            cogX = sumMassX / sumMass;
            cogY = sumMassY / sumMass;
            cogZ = sumMassZ / sumMass;

            if (Double.isNaN(cogX) || Double.isInfinite(cogX) ||
                Double.isNaN(cogY) || Double.isInfinite(cogY) ||
                Double.isNaN(cogZ) || Double.isInfinite(cogZ)) {
                findings.add(new ValidationFindingDto(
                        "CRITICAL", "NON_FINITE_COG",
                        "Calculated Centre of Gravity resulted in non-finite coordinates: (" + cogX + ", " + cogY + ", " + cogZ + ")",
                        List.of()
                ));
                loadBalanceStatus = "INVALID_DATA";
            } else {
                // Check if authoritative compartment CoG envelope limits are configured
                boolean hasConfiguredLimits = false;
                boolean outOfBounds = false;

                if (compartment != null) {
                    if (compartment.getMinCogXMm() != null && cogX < compartment.getMinCogXMm()) {
                        outOfBounds = true;
                        hasConfiguredLimits = true;
                        findings.add(new ValidationFindingDto(
                                "CRITICAL", "COG_OUT_OF_BOUNDS",
                                "Longitudinal CoG (" + String.format("%.2f", cogX) + " mm) is forward of configured minimum boundary (" + compartment.getMinCogXMm() + " mm)",
                                List.of()
                        ));
                    }
                    if (compartment.getMaxCogXMm() != null && cogX > compartment.getMaxCogXMm()) {
                        outOfBounds = true;
                        hasConfiguredLimits = true;
                        findings.add(new ValidationFindingDto(
                                "CRITICAL", "COG_OUT_OF_BOUNDS",
                                "Longitudinal CoG (" + String.format("%.2f", cogX) + " mm) is rear of configured maximum boundary (" + compartment.getMaxCogXMm() + " mm)",
                                List.of()
                        ));
                    }
                    if (compartment.getMinCogYMm() != null && cogY < compartment.getMinCogYMm()) {
                        outOfBounds = true;
                        hasConfiguredLimits = true;
                        findings.add(new ValidationFindingDto(
                                "CRITICAL", "COG_OUT_OF_BOUNDS",
                                "Lateral CoG (" + String.format("%.2f", cogY) + " mm) is left of configured minimum boundary (" + compartment.getMinCogYMm() + " mm)",
                                List.of()
                        ));
                    }
                    if (compartment.getMaxCogYMm() != null && cogY > compartment.getMaxCogYMm()) {
                        outOfBounds = true;
                        hasConfiguredLimits = true;
                        findings.add(new ValidationFindingDto(
                                "CRITICAL", "COG_OUT_OF_BOUNDS",
                                "Lateral CoG (" + String.format("%.2f", cogY) + " mm) is right of configured maximum boundary (" + compartment.getMaxCogYMm() + " mm)",
                                List.of()
                        ));
                    }
                    if (compartment.getMinCogZMm() != null && cogZ < compartment.getMinCogZMm()) {
                        outOfBounds = true;
                        hasConfiguredLimits = true;
                        findings.add(new ValidationFindingDto(
                                "CRITICAL", "COG_OUT_OF_BOUNDS",
                                "Vertical CoG (" + String.format("%.2f", cogZ) + " mm) is below configured minimum boundary (" + compartment.getMinCogZMm() + " mm)",
                                List.of()
                        ));
                    }
                    if (compartment.getMaxCogZMm() != null && cogZ > compartment.getMaxCogZMm()) {
                        outOfBounds = true;
                        hasConfiguredLimits = true;
                        findings.add(new ValidationFindingDto(
                                "CRITICAL", "COG_OUT_OF_BOUNDS",
                                "Vertical CoG (" + String.format("%.2f", cogZ) + " mm) exceeds configured maximum boundary (" + compartment.getMaxCogZMm() + " mm)",
                                List.of()
                        ));
                    }

                    if (!hasConfiguredLimits) {
                        hasConfiguredLimits = (compartment.getMinCogXMm() != null || compartment.getMaxCogXMm() != null ||
                                               compartment.getMinCogYMm() != null || compartment.getMaxCogYMm() != null ||
                                               compartment.getMinCogZMm() != null || compartment.getMaxCogZMm() != null);
                    }
                }

                if (outOfBounds) {
                    loadBalanceStatus = "OUT_OF_BOUNDS";
                } else if (hasConfiguredLimits) {
                    loadBalanceStatus = "BALANCED";
                } else {
                    // Do not claim regulatory or physical compliance when limits are not configured.
                    loadBalanceStatus = "NOT_CONFIGURED";
                }
            }
        }

        boolean passed = findings.isEmpty();
        ValidationResultDto result = new ValidationResultDto(
                UUID.randomUUID().toString(),
                passed,
                VALIDATOR_VERSION,
                ruleSnapshot != null && ruleSnapshot.getRules() != null ? ruleSnapshot.getRules().size() : 0,
                findings
        );
        result.setCogXMm(cogX);
        result.setCogYMm(cogY);
        result.setCogZMm(cogZ);
        result.setTotalMassKg(sumMass);
        result.setLoadBalanceStatus(loadBalanceStatus);
        return result;
    }

    private double calculateEuclideanDistance(ItemPlacementDto a, ItemPlacementDto b) {
        // Center-to-center distance approximation in 3D
        double cxa = a.getXMm() + a.getLengthMm() / 2.0;
        double cya = a.getYMm() + a.getWidthMm() / 2.0;
        double cza = a.getZMm() + a.getHeightMm() / 2.0;

        double cxb = b.getXMm() + b.getLengthMm() / 2.0;
        double cyb = b.getYMm() + b.getWidthMm() / 2.0;
        double czb = b.getZMm() + b.getHeightMm() / 2.0;

        return Math.sqrt(Math.pow(cxa - cxb, 2) + Math.pow(cya - cyb, 2) + Math.pow(cza - czb, 2));
    }
}
