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

        boolean passed = findings.isEmpty();
        return new ValidationResultDto(
                UUID.randomUUID().toString(),
                passed,
                VALIDATOR_VERSION,
                ruleSnapshot != null && ruleSnapshot.getRules() != null ? ruleSnapshot.getRules().size() : 0,
                findings
        );
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
