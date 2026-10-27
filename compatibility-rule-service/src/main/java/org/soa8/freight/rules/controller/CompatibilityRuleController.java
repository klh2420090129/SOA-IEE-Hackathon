package org.soa8.freight.rules.controller;

import org.soa8.freight.common.model.CompatibilityRuleDto;
import org.soa8.freight.common.model.RuleSnapshotDto;
import org.soa8.freight.rules.model.CompatibilityRuleEntity;
import org.soa8.freight.rules.model.RuleSnapshotEntity;
import org.soa8.freight.rules.repository.CompatibilityRuleRepository;
import org.soa8.freight.rules.repository.RuleSnapshotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class CompatibilityRuleController {

    private final CompatibilityRuleRepository ruleRepository;
    private final RuleSnapshotRepository snapshotRepository;

    public CompatibilityRuleController(CompatibilityRuleRepository ruleRepository,
                                       RuleSnapshotRepository snapshotRepository) {
        this.ruleRepository = ruleRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @GetMapping("/rules")
    public List<CompatibilityRuleDto> getRules() {
        return ruleRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/rule-snapshots/{id}")
    public ResponseEntity<?> getSnapshot(@PathVariable String id) {
        return snapshotRepository.findById(id)
                .map(this::toDto)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "SNAPSHOT_NOT_FOUND",
                        "message", "Rule snapshot not found for id: " + id
                )));
    }

    private CompatibilityRuleDto toDto(CompatibilityRuleEntity entity) {
        return new CompatibilityRuleDto(
                entity.getRuleId(),
                entity.getRuleCode(),
                entity.getPrimaryHazardClass(),
                entity.getSecondaryHazardClass(),
                entity.getSegregationType(),
                entity.getMinDistanceMm(),
                entity.getDescription(),
                entity.getJurisdiction()
        );
    }

    private RuleSnapshotDto toDto(RuleSnapshotEntity entity) {
        List<CompatibilityRuleDto> ruleDtos = entity.getRules().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return new RuleSnapshotDto(
                entity.getSnapshotId(),
                entity.getJurisdiction(),
                entity.getEffectiveDate(),
                entity.getStatus(),
                entity.getSnapshotDigest(),
                ruleDtos
        );
    }
}
