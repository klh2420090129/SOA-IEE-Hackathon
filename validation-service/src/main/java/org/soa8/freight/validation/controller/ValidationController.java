package org.soa8.freight.validation.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.soa8.freight.common.model.*;
import org.soa8.freight.validation.engine.IndependentValidator;
import org.soa8.freight.validation.model.ValidationRunEntity;
import org.soa8.freight.validation.repository.ValidationRunRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/validations")
public class ValidationController {

    private final IndependentValidator validator;
    private final ValidationRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public ValidationController(IndependentValidator validator,
                                ValidationRunRepository runRepository,
                                ObjectMapper objectMapper) {
        this.validator = validator;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    public static class RunValidationRequest {
        public CandidateLayoutDto layout;
        public CompartmentDto compartment;
        public RuleSnapshotDto ruleSnapshot;
    }

    @PostMapping
    public ResponseEntity<?> runValidation(@RequestBody RunValidationRequest req) {
        if (req.layout == null || req.compartment == null) {
            return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", "Layout and compartment are required"));
        }

        ValidationResultDto result = validator.validate(req.layout, req.compartment, req.ruleSnapshot);
        String candidateDigest = req.layout.getContentDigest() != null ? req.layout.getContentDigest() : "";
        String snapshotId = req.ruleSnapshot != null ? req.ruleSnapshot.getSnapshotId() : "none";
        String snapshotDigest = req.ruleSnapshot != null ? req.ruleSnapshot.getSnapshotDigest() : "";

        result.setCandidateLayoutId(req.layout.getLayoutId());
        result.setCandidateLayoutVersion(req.layout.getLayoutVersion());
        result.setCandidateContentDigest(candidateDigest);
        result.setRuleSnapshotId(snapshotId);
        result.setRuleSnapshotDigest(snapshotDigest);

        try {
            String findingsJson = objectMapper.writeValueAsString(result.getFindings());
            ValidationRunEntity run = new ValidationRunEntity(
                    result.getValidationId(),
                    req.layout.getLayoutId(),
                    req.layout.getLayoutVersion(),
                    candidateDigest,
                    snapshotId,
                    snapshotDigest,
                    result.isPassed(),
                    result.getValidatorVersion(),
                    result.getFindings().size(),
                    findingsJson
            );
            runRepository.save(run);
        } catch (JsonProcessingException e) {
            // Non-fatal for client response
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getValidationRun(@PathVariable("id") String id) {
        return runRepository.findById(id)
                .<ResponseEntity<?>>map(run -> {
                    List<ValidationFindingDto> findings = List.of();
                    if (run.getFindingsJson() != null && !run.getFindingsJson().isBlank()) {
                        try {
                            findings = objectMapper.readValue(
                                    run.getFindingsJson(),
                                    objectMapper.getTypeFactory().constructCollectionType(List.class, ValidationFindingDto.class)
                            );
                        } catch (Exception e) {
                            findings = List.of();
                        }
                    }
                    ValidationResultDto dto = new ValidationResultDto(
                            run.getValidationId(),
                            run.isPassed(),
                            run.getValidatorVersion(),
                            run.getFindingsCount(),
                            findings
                    );
                    dto.setCandidateLayoutId(run.getCandidateLayoutId());
                    dto.setCandidateLayoutVersion(run.getCandidateLayoutVersion());
                    dto.setCandidateContentDigest(run.getCandidateContentDigest());
                    dto.setRuleSnapshotId(run.getRuleSnapshotId());
                    dto.setRuleSnapshotDigest(run.getRuleSnapshotDigest());
                    return ResponseEntity.ok(dto);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
