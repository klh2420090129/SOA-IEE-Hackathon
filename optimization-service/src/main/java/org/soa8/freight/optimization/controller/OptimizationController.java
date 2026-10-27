package org.soa8.freight.optimization.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.soa8.freight.common.model.CandidateLayoutDto;
import org.soa8.freight.common.model.CargoItemDto;
import org.soa8.freight.common.model.CompartmentDto;
import org.soa8.freight.optimization.model.OptimizationJobEntity;
import org.soa8.freight.optimization.repository.OptimizationJobRepository;
import org.soa8.freight.optimization.stub.ControlledOptimizerStub;
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

    public OptimizationController(OptimizationJobRepository jobRepository,
                                  ControlledOptimizerStub optimizerStub,
                                  ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.optimizerStub = optimizerStub;
        this.objectMapper = objectMapper;
    }

    public static class CreateOptimizationJobRequest {
        public String declarationId;
        public CompartmentDto compartment;
        public List<CargoItemDto> items;
    }

    @PostMapping
    public ResponseEntity<?> submitJob(@RequestBody CreateOptimizationJobRequest req) {
        if (req.declarationId == null || req.compartment == null || req.items == null) {
            return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", "Missing required fields"));
        }

        String jobId = UUID.randomUUID().toString();
        OptimizationJobEntity job = new OptimizationJobEntity(jobId, req.declarationId, req.compartment.getCompartmentId());

        // In Milestone 1, solve synchronously using the Controlled Optimizer Stub
        CandidateLayoutDto stubResult = optimizerStub.generateControlledStubLayout(
                req.compartment.getCompartmentId(),
                req.compartment.getLengthMm(),
                req.compartment.getWidthMm(),
                req.compartment.getHeightMm(),
                req.items
        );

        try {
            job.setResultJson(objectMapper.writeValueAsString(stubResult));
            job.setStatus("COMPLETED");
            job.setSolverStatus(stubResult.getSolverStatus());
            job.setProvenance(stubResult.getProvenance());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
        } catch (JsonProcessingException e) {
            return ResponseEntity.internalServerError().body(Map.of("code", "SERIALIZATION_ERROR", "message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable String id) {
        return jobRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", "Job not found: " + id)));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<?> getJobResult(@PathVariable String id) {
        return jobRepository.findById(id)
                .<ResponseEntity<?>>map(job -> {
                    try {
                        CandidateLayoutDto result = objectMapper.readValue(job.getResultJson(), CandidateLayoutDto.class);
                        return ResponseEntity.ok(result);
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of("code", "PARSE_ERROR", "message", e.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", "Job not found: " + id)));
    }
}
