package org.soa8.freight.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.*;
import org.soa8.freight.optimization.consumer.OptimizationResultConsumer;
import org.soa8.freight.optimization.controller.OptimizationController;
import org.soa8.freight.optimization.model.*;
import org.soa8.freight.optimization.repository.OptimizationJobRepository;
import org.soa8.freight.optimization.repository.OptimizationOutboxRepository;
import org.soa8.freight.optimization.repository.ProcessedMessageRepository;
import org.soa8.freight.optimization.service.OptimizationJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false",
    "optimization.rules.allow-test-fallback=true"
})
class OptimizationAsyncWorkflowTest {

    @Autowired
    private OptimizationController controller;

    @Autowired
    private OptimizationJobService jobService;

    @Autowired
    private OptimizationJobRepository jobRepository;

    @Autowired
    private OptimizationOutboxRepository outboxRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private OptimizationResultConsumer resultConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("1. Durable job creation and outbox atomicity when async=true")
    void testDurableJobCreationAndOutboxAtomicity() {
        OptimizationController.CreateOptimizationJobRequest req = new OptimizationController.CreateOptimizationJobRequest();
        req.declarationId = "decl-async-001";
        req.compartment = new CompartmentDto("comp-test-1", "Standard Trailer", 12000, 2400, 2600, 25000);
        req.items = List.of(
                new CargoItemDto("it-box-1", "Drums", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1)
        );
        req.ruleSnapshotId = "snap-2026-q4-v1";
        req.timeLimitSeconds = 20;
        req.async = true;

        ResponseEntity<?> response = controller.submitJob(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        OptimizationJobEntity job = (OptimizationJobEntity) response.getBody();
        assertThat(job).isNotNull();
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUBMITTED);
        assertThat(job.getCurrentAttemptId()).isNotBlank();

        // Verify outbox record was persisted in same transaction
        List<OptimizationOutboxEntity> outbox = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        assertThat(outbox).anyMatch(e -> e.getAggregateId().equals(job.getJobId()) && "OptimizationCommandDispatched".equals(e.getEventType()));
    }

    @Test
    @DisplayName("2. Idempotent result acceptance updates job to COMPLETED and stores CandidateLayout")
    void testIdempotentResultAcceptance() throws Exception {
        // Create job
        OptimizationJobEntity job = jobService.submitJobDurable(
                "decl-async-002",
                new CompartmentDto("comp-test-1", "Standard Trailer", 12000, 2400, 2600, 25000),
                List.of(new CargoItemDto("it-1", "Drums", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1)),
                null,
                "snap-2026-q4-v1",
                "PYTHON_WORKER",
                20
        );

        ItemPlacementDto placement = new ItemPlacementDto("p1", "it-1", 0, "3", "UN1993", 0, 0, 0, 1000, 1000, 1000, 400, "DEFAULT", true, 800.0);
        OptimizationResultDto result = new OptimizationResultDto(
                "res-001",
                job.getJobId(),
                job.getCurrentAttemptId(),
                job.getCorrelationId(),
                "FEASIBLE",
                "PYTHON_HEURISTIC_EP_3D_V1",
                150L,
                "snap-2026-q4-v1",
                "digest-123",
                "cand-digest-abc",
                List.of(placement),
                List.of(),
                Map.of("unitsPlaced", 1),
                null
        );

        String resultJson = objectMapper.writeValueAsString(result);

        // First delivery: accepts result
        resultConsumer.consumeOptimizationResult(resultJson);

        OptimizationJobEntity updatedJob = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(updatedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(updatedJob.getSolverStatus()).isEqualTo("FEASIBLE");
        assertThat(updatedJob.getProvenance()).isEqualTo("PYTHON_HEURISTIC_EP_3D_V1");
        assertThat(updatedJob.getResultJson()).isNotBlank();

        // Verify deduplication entity exists
        assertThat(processedMessageRepository.existsById("res-001")).isTrue();

        // Second delivery (duplicate): should be skipped idempotently
        resultConsumer.consumeOptimizationResult(resultJson);
        OptimizationJobEntity afterDuplicateJob = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(afterDuplicateJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("3. Stale attempt result rejection")
    void testStaleAttemptResultRejection() throws Exception {
        OptimizationJobEntity job = jobService.submitJobDurable(
                "decl-async-003",
                new CompartmentDto("comp-test-1", "Standard Trailer", 12000, 2400, 2600, 25000),
                List.of(new CargoItemDto("it-1", "Drums", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1)),
                null,
                "snap-2026-q4-v1",
                "PYTHON_WORKER",
                20
        );

        // Result targeting an outdated/stale attempt
        OptimizationResultDto staleResult = new OptimizationResultDto(
                "res-stale",
                job.getJobId(),
                "att-stale-attempt-999",
                job.getCorrelationId(),
                "FEASIBLE",
                "PYTHON_HEURISTIC_EP_3D_V1",
                150L,
                "snap-2026-q4-v1",
                "digest-123",
                "cand-digest-abc",
                List.of(),
                List.of(),
                Map.of(),
                null
        );

        resultConsumer.consumeOptimizationResult(objectMapper.writeValueAsString(staleResult));

        OptimizationJobEntity currentJob = jobRepository.findById(job.getJobId()).orElseThrow();
        // Job must NOT transition to COMPLETED on a stale attempt
        assertThat(currentJob.getStatus()).isEqualTo(JobStatus.SUBMITTED);
    }

    @Test
    @DisplayName("4. Rule snapshot mismatch results in job failure")
    void testRuleSnapshotMismatchFailsJob() throws Exception {
        OptimizationJobEntity job = jobService.submitJobDurable(
                "decl-async-004",
                new CompartmentDto("comp-test-1", "Standard Trailer", 12000, 2400, 2600, 25000),
                List.of(new CargoItemDto("it-1", "Drums", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1)),
                null,
                "snap-2026-q4-v1",
                "PYTHON_WORKER",
                20
        );

        // Result with wrong ruleSnapshotId
        OptimizationResultDto mismatchResult = new OptimizationResultDto(
                "res-mismatch",
                job.getJobId(),
                job.getCurrentAttemptId(),
                job.getCorrelationId(),
                "FEASIBLE",
                "PYTHON_HEURISTIC_EP_3D_V1",
                150L,
                "wrong-snapshot-id-2025",
                "digest-wrong",
                "cand-digest-abc",
                List.of(),
                List.of(),
                Map.of(),
                null
        );

        resultConsumer.consumeOptimizationResult(objectMapper.writeValueAsString(mismatchResult));

        OptimizationJobEntity failedJob = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(failedJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failedJob.getErrorMessage()).contains("Rule snapshot mismatch");
    }

    @Test
    @DisplayName("5. Missing or invalid worker solverStatus is rejected fail-closed and cannot become feasible")
    void testMissingOrInvalidSolverStatusRejected() throws Exception {
        OptimizationJobEntity job = jobService.submitJobDurable(
                "decl-async-005",
                new CompartmentDto("comp-test-1", "Standard Trailer", 12000, 2400, 2600, 25000),
                List.of(new CargoItemDto("it-1", "Drums", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1)),
                null,
                "snap-2026-q4-v1",
                "PYTHON_WORKER",
                20
        );

        // Result missing solverStatus entirely
        OptimizationResultDto nullStatusResult = new OptimizationResultDto(
                "res-null-status",
                job.getJobId(),
                job.getCurrentAttemptId(),
                job.getCorrelationId(),
                null, // Null solverStatus!
                "PYTHON_HEURISTIC_EP_3D_V1",
                150L,
                "snap-2026-q4-v1",
                "digest-123",
                "cand-digest-abc",
                List.of(),
                List.of(),
                Map.of(),
                null
        );

        resultConsumer.consumeOptimizationResult(objectMapper.writeValueAsString(nullStatusResult));

        OptimizationJobEntity currentJob = jobRepository.findById(job.getJobId()).orElseThrow();
        // Job must NOT be marked COMPLETED or FEASIBLE
        assertThat(currentJob.getStatus()).isEqualTo(JobStatus.SUBMITTED);
        assertThat(currentJob.getSolverStatus()).isEqualTo("UNKNOWN");

        // Result with invalid garbage solverStatus
        OptimizationResultDto invalidStatusResult = new OptimizationResultDto(
                "res-invalid-status",
                job.getJobId(),
                job.getCurrentAttemptId(),
                job.getCorrelationId(),
                "INVALID_STATUS_VALUE",
                "PYTHON_HEURISTIC_EP_3D_V1",
                150L,
                "snap-2026-q4-v1",
                "digest-123",
                "cand-digest-abc",
                List.of(),
                List.of(),
                Map.of(),
                null
        );

        resultConsumer.consumeOptimizationResult(objectMapper.writeValueAsString(invalidStatusResult));

        OptimizationJobEntity stillCurrentJob = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(stillCurrentJob.getStatus()).isEqualTo(JobStatus.SUBMITTED);
        assertThat(stillCurrentJob.getSolverStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("6. Timeout recovery transitions expired jobs to TIMEOUT and late results are rejected")
    void testTimeoutRecoveryAndLateResultRejection() throws Exception {
        OptimizationJobEntity job = jobService.submitJobDurable(
                "decl-async-006",
                new CompartmentDto("comp-test-1", "Standard Trailer", 12000, 2400, 2600, 25000),
                List.of(new CargoItemDto("it-1", "Drums", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1)),
                null,
                "snap-2026-q4-v1",
                "PYTHON_WORKER",
                2 // 2 seconds deadline
        );

        // Fast-forward or backdate created time to simulate expiration
        job.setCreatedAt(java.time.Instant.now().minusSeconds(10));
        jobRepository.save(job);

        // Execute recovery sweep
        int timedOutCount = jobService.recoverStuckJobs();
        assertThat(timedOutCount).isGreaterThanOrEqualTo(1);

        OptimizationJobEntity timedOutJob = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(timedOutJob.getStatus()).isEqualTo(JobStatus.TIMEOUT);
        assertThat(timedOutJob.getSolverStatus()).isEqualTo("TIMEOUT");

        // Late worker result arrives after timeout
        OptimizationResultDto lateResult = new OptimizationResultDto(
                "res-late-001",
                job.getJobId(),
                job.getCurrentAttemptId(),
                job.getCorrelationId(),
                "FEASIBLE",
                "PYTHON_HEURISTIC_EP_3D_V1",
                5000L,
                "snap-2026-q4-v1",
                "digest-123",
                "cand-digest-abc",
                List.of(),
                List.of(),
                Map.of(),
                null
        );

        resultConsumer.consumeOptimizationResult(objectMapper.writeValueAsString(lateResult));

        OptimizationJobEntity postLateResultJob = jobRepository.findById(job.getJobId()).orElseThrow();
        // Preserves terminal TIMEOUT state and cannot be overwritten
        assertThat(postLateResultJob.getStatus()).isEqualTo(JobStatus.TIMEOUT);
        assertThat(postLateResultJob.getSolverStatus()).isEqualTo("TIMEOUT");
    }
}

