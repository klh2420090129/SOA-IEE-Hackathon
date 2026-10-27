package org.soa8.freight.optimization.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.soa8.freight.common.model.CandidateLayoutDto;
import org.soa8.freight.common.model.CargoItemDto;
import org.soa8.freight.common.model.CompartmentDto;
import org.soa8.freight.common.model.SolverStatus;
import org.soa8.freight.optimization.client.RuleSnapshotClient;
import org.soa8.freight.optimization.engine.Deterministic3DHeuristicOptimizer;
import org.soa8.freight.optimization.model.JobStatus;
import org.soa8.freight.optimization.model.OptimizationJobEntity;
import org.soa8.freight.optimization.repository.OptimizationJobRepository;
import org.soa8.freight.optimization.stub.ControlledOptimizerStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/optimization-jobs")
public class OptimizationController {

    private final OptimizationJobRepository jobRepository;
    private final ControlledOptimizerStub optimizerStub;
    private final ObjectMapper objectMapper;
    private final Deterministic3DHeuristicOptimizer heuristicOptimizer;
    private final RuleSnapshotClient ruleSnapshotClient;
    private final int defaultTimeLimitSeconds;
    private final int maxTimeLimitSeconds;

    public OptimizationController(
            OptimizationJobRepository jobRepository,
            ControlledOptimizerStub optimizerStub,
            Deterministic3DHeuristicOptimizer heuristicOptimizer,
            RuleSnapshotClient ruleSnapshotClient,
            ObjectMapper objectMapper,
            @Value("${optimization.solver.default-time-limit-seconds:20}") int defaultTimeLimitSeconds,
            @Value("${optimization.solver.max-time-limit-seconds:30}") int maxTimeLimitSeconds) {
        this.jobRepository = jobRepository;
        this.optimizerStub = optimizerStub;
        this.heuristicOptimizer = heuristicOptimizer;
        this.ruleSnapshotClient = ruleSnapshotClient;
        this.objectMapper = objectMapper;
        this.defaultTimeLimitSeconds = defaultTimeLimitSeconds;
        this.maxTimeLimitSeconds = maxTimeLimitSeconds;
    }

    public static class CreateOptimizationJobRequest {
        public String declarationId;
        public CompartmentDto compartment;
        public List<CargoItemDto> items;
        public String ruleSnapshotId;
        public Integer timeLimitSeconds;
        public String algorithm; // Optional: "HEURISTIC_EP_3D_V1" (default) or "CONTROLLED_STUB"
    }

    public static class UpdateJobOutcomeRequest {
        public SolverStatus solverStatus;
        public String provenance;
        public CandidateLayoutDto candidateLayout;
        public String errorMessage;
    }

    @PostMapping
    public ResponseEntity<?> submitJob(@RequestBody CreateOptimizationJobRequest req) {
        if (req.declarationId == null || req.declarationId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", "declarationId is required"));
        }
        if (req.compartment == null || req.compartment.getCompartmentId() == null) {
            return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", "compartment with compartmentId is required"));
        }
        if (req.items == null) {
            return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", "items list is required"));
        }

        // Validate time limit budget
        int timeLimit = (req.timeLimitSeconds != null && req.timeLimitSeconds > 0)
                ? req.timeLimitSeconds : defaultTimeLimitSeconds;
        if (timeLimit > maxTimeLimitSeconds) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_TIMEOUT_BUDGET",
                    "message", "timeLimitSeconds (" + timeLimit + ") exceeds maximum permitted ceiling (" + maxTimeLimitSeconds + "s)"
            ));
        }

        // Validate pinned rule snapshot identity
        String snapshotId = (req.ruleSnapshotId != null && !req.ruleSnapshotId.isBlank())
                ? req.ruleSnapshotId : "snap-2026-q4-v1";
        if (snapshotId.trim().isEmpty() || "null".equalsIgnoreCase(snapshotId)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_RULE_SNAPSHOT",
                    "message", "A valid immutable ruleSnapshotId is required to bind optimization job"
            ));
        }

        // Resolve rule snapshot for deterministic segregation evaluation
        org.soa8.freight.common.model.RuleSnapshotDto resolvedSnapshot = ruleSnapshotClient.resolveSnapshot(snapshotId)
                .orElse(null);

        if (resolvedSnapshot == null && !ControlledOptimizerStub.PROVENANCE_STUB.equalsIgnoreCase(req.algorithm)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "code", "RULE_SNAPSHOT_UNAVAILABLE",
                    "message", "Mandatory immutable rule snapshot '" + snapshotId + "' is missing, unreviewed, or unavailable. Failing closed."
            ));
        }

        String jobId = UUID.randomUUID().toString();
        OptimizationJobEntity job = new OptimizationJobEntity(
                jobId, req.declarationId, req.compartment.getCompartmentId(), snapshotId
        );
        job.setTimeLimitSeconds(timeLimit);
        job.setStartedAt(Instant.now());

        // Select optimizer: Default to genuine Deterministic3DHeuristicOptimizer, or CONTROLLED_STUB if explicitly requested
        CandidateLayoutDto layoutResult;
        long startMs = System.currentTimeMillis();

        if (ControlledOptimizerStub.PROVENANCE_STUB.equalsIgnoreCase(req.algorithm)) {
            layoutResult = optimizerStub.generateControlledStubLayout(
                    req.compartment.getCompartmentId(),
                    req.compartment.getLengthMm(),
                    req.compartment.getWidthMm(),
                    req.compartment.getHeightMm(),
                    req.items,
                    snapshotId
            );
        } else {
            try {
                layoutResult = heuristicOptimizer.optimize(
                        req.compartment,
                        req.items,
                        resolvedSnapshot,
                        snapshotId
                );
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", "INVALID_CARGO_INPUT",
                        "message", e.getMessage()
                ));
            }
        }

        long durationMs = System.currentTimeMillis() - startMs;
        layoutResult.setExecutionDurationMs(durationMs);

        try {
            job.setResultJson(objectMapper.writeValueAsString(layoutResult));
            job.transitionTo(JobStatus.COMPLETED);
            job.setSolverStatus(layoutResult.getSolverStatus());
            job.setProvenance(layoutResult.getProvenance());
            job.setExecutionDurationMs(durationMs);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
        } catch (JsonProcessingException e) {
            job.transitionTo(JobStatus.FAILED);
            job.setErrorMessage("Serialization failure: " + e.getMessage());
            jobRepository.save(job);
            return ResponseEntity.internalServerError().body(Map.of("code", "SERIALIZATION_ERROR", "message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable("id") String id) {
        return jobRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "NOT_FOUND",
                        "message", "Job not found: " + id
                )));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getJobStatus(@PathVariable("id") String id) {
        return jobRepository.findById(id)
                .<ResponseEntity<?>>map(job -> {
                    java.util.Map<String, Object> statusMap = new java.util.LinkedHashMap<>();
                    statusMap.put("jobId", job.getJobId());
                    statusMap.put("declarationId", job.getDeclarationId());
                    statusMap.put("compartmentId", job.getCompartmentId());
                    statusMap.put("ruleSnapshotId", job.getRuleSnapshotId());
                    statusMap.put("status", job.getStatus().name());
                    statusMap.put("solverStatus", job.getSolverStatus());
                    statusMap.put("provenance", job.getProvenance());
                    statusMap.put("timeLimitSeconds", job.getTimeLimitSeconds());
                    statusMap.put("executionDurationMs", job.getExecutionDurationMs() != null ? job.getExecutionDurationMs() : 0);
                    statusMap.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : "");
                    statusMap.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : "");
                    return ResponseEntity.ok(statusMap);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "NOT_FOUND",
                        "message", "Job not found: " + id
                )));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<?> getJobResult(@PathVariable("id") String id) {
        return jobRepository.findById(id)
                .<ResponseEntity<?>>map(job -> {
                    if (job.getStatus() == JobStatus.SUBMITTED || job.getStatus() == JobStatus.RUNNING) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                                "code", "JOB_NOT_READY",
                                "message", "Optimization job is still in status: " + job.getStatus()
                        ));
                    }
                    if (job.getStatus() == JobStatus.FAILED || job.getStatus() == JobStatus.TIMEOUT || job.getStatus() == JobStatus.CANCELLED) {
                        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                                "code", "JOB_" + job.getStatus().name(),
                                "message", job.getErrorMessage() != null ? job.getErrorMessage() : "Job terminated in state: " + job.getStatus()
                        ));
                    }
                    try {
                        CandidateLayoutDto result = objectMapper.readValue(job.getResultJson(), CandidateLayoutDto.class);
                        return ResponseEntity.ok(result);
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of("code", "PARSE_ERROR", "message", e.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "NOT_FOUND",
                        "message", "Job not found: " + id
                )));
    }
}
