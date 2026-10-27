package org.soa8.freight.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.*;
import org.soa8.freight.optimization.controller.OptimizationController;
import org.soa8.freight.optimization.engine.Deterministic3DHeuristicOptimizer;
import org.soa8.freight.optimization.model.JobStatus;
import org.soa8.freight.optimization.model.OptimizationJobEntity;
import org.soa8.freight.optimization.repository.OptimizationJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "optimization.solver.default-time-limit-seconds=20",
    "optimization.solver.max-time-limit-seconds=30"
})
class OptimizationJobLifecycleTest {

    @Autowired
    private OptimizationController controller;

    @Autowired
    private OptimizationJobRepository jobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("1. Successful job submission preserves exact snapshot and yields COMPLETED status with FEASIBLE stub result")
    void shouldSuccessfullySubmitAndCompleteJobWithSnapshot() {
        OptimizationController.CreateOptimizationJobRequest req = new OptimizationController.CreateOptimizationJobRequest();
        req.declarationId = "decl-phaseA-001";
        req.compartment = new CompartmentDto("comp-test-1", "Standard Trailer", 12000, 2400, 2600, 25000);
        req.items = List.of(
                new CargoItemDto("it-drum-1", "Solvent", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1)
        );
        req.ruleSnapshotId = "snap-2026-q4-v1";
        req.timeLimitSeconds = 15;

        ResponseEntity<?> response = controller.submitJob(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        OptimizationJobEntity job = (OptimizationJobEntity) response.getBody();
        assertThat(job).isNotNull();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.FEASIBLE.name());
        assertThat(job.getProvenance()).isEqualTo(Deterministic3DHeuristicOptimizer.ALGORITHM_PROVENANCE);
        assertThat(job.getRuleSnapshotId()).isEqualTo("snap-2026-q4-v1");
        assertThat(job.getTimeLimitSeconds()).isEqualTo(15);
        assertThat(job.getExecutionDurationMs()).isNotNull();

        // Verify retrieval via GET /{id}
        ResponseEntity<?> getResp = controller.getJob(job.getJobId());
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify retrieval via GET /{id}/status
        ResponseEntity<?> statusResp = controller.getJobStatus(job.getJobId());
        assertThat(statusResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> statusMap = (Map<?, ?>) statusResp.getBody();
        assertThat(statusMap.get("status")).isEqualTo("COMPLETED");
        assertThat(statusMap.get("solverStatus")).isEqualTo("FEASIBLE");

        // Verify retrieval via GET /{id}/result
        ResponseEntity<?> resultResp = controller.getJobResult(job.getJobId());
        assertThat(resultResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        CandidateLayoutDto layout = (CandidateLayoutDto) resultResp.getBody();
        assertThat(layout.getRuleSnapshotId()).isEqualTo("snap-2026-q4-v1");
        assertThat(layout.getPlacements()).hasSize(1);
    }

    @Test
    @DisplayName("2. Separation of Job Execution Status (COMPLETED) from Solver Outcome (FEASIBLE)")
    void shouldSeparateExecutionStatusFromSolverStatus() {
        OptimizationJobEntity job = new OptimizationJobEntity("job-sep-1", "decl-sep-1", "comp-sep-1", "snap-2026-q4-v1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUBMITTED);
        assertThat(job.getSolverStatus()).isEqualTo("UNKNOWN");

        job.transitionTo(JobStatus.RUNNING);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);

        job.transitionTo(JobStatus.COMPLETED);
        job.setSolverStatus(SolverStatus.FEASIBLE.name());

        assertThat(job.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(job.getSolverStatus()).isEqualTo("FEASIBLE");
        assertThat(job.getStatus().name()).isNotEqualTo(job.getSolverStatus());
    }

    @Test
    @DisplayName("3. Enforce valid state transitions and reject invalid transitions from terminal states")
    void shouldGuardAgainstIllegalStateTransitions() {
        OptimizationJobEntity job = new OptimizationJobEntity("job-guard-1", "decl-guard-1", "comp-guard-1", "snap-2026-q4-v1");
        job.transitionTo(JobStatus.RUNNING);
        job.transitionTo(JobStatus.COMPLETED);

        // Transition from terminal COMPLETED -> RUNNING must throw IllegalStateException
        assertThatThrownBy(() -> job.transitionTo(JobStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal job status transition from COMPLETED to RUNNING");

        // Transition from terminal COMPLETED -> SUBMITTED must throw IllegalStateException
        assertThatThrownBy(() -> job.transitionTo(JobStatus.SUBMITTED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("4. Rejection of timeout exceeding configured maximum ceiling (30s)")
    void shouldRejectTimeoutExceedingMaxCeiling() {
        OptimizationController.CreateOptimizationJobRequest req = new OptimizationController.CreateOptimizationJobRequest();
        req.declarationId = "decl-timeout-001";
        req.compartment = new CompartmentDto("comp-test-1", "Standard Trailer", 12000, 2400, 2600, 25000);
        req.items = List.of(new CargoItemDto("it-1", "Drums", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1));
        req.timeLimitSeconds = 45; // exceeds ceiling 30s

        ResponseEntity<?> response = controller.submitJob(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> err = (Map<?, ?>) response.getBody();
        assertThat(err.get("code")).isEqualTo("INVALID_TIMEOUT_BUDGET");
    }

    @Test
    @DisplayName("5. Serialization and deserialization of all 5 SolverStatus values")
    void shouldSerializeAndDeserializeAllSolverStatuses() throws Exception {
        for (SolverStatus status : SolverStatus.values()) {
            CandidateLayoutDto layout = new CandidateLayoutDto(
                    "layout-" + status, 1, "comp-1", "digest", status.name(), "TEST_PROVENANCE", List.of()
            );
            String json = objectMapper.writeValueAsString(layout);
            CandidateLayoutDto parsed = objectMapper.readValue(json, CandidateLayoutDto.class);
            assertThat(parsed.getSolverStatus()).isEqualTo(status.name());
        }
    }

    @Test
    @DisplayName("6. Backward compatibility: default snapshot and timeout when not explicitly supplied")
    void shouldSupportBackwardCompatibleSubmission() {
        OptimizationController.CreateOptimizationJobRequest req = new OptimizationController.CreateOptimizationJobRequest();
        req.declarationId = "decl-compat-001";
        req.compartment = new CompartmentDto("comp-test-1", "Standard Trailer", 12000, 2400, 2600, 25000);
        req.items = List.of(new CargoItemDto("it-1", "Drums", "3", "UN1993", 1000, 1000, 1000, 400, true, 800, 1));
        // ruleSnapshotId and timeLimitSeconds omitted

        ResponseEntity<?> response = controller.submitJob(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        OptimizationJobEntity job = (OptimizationJobEntity) response.getBody();
        assertThat(job.getRuleSnapshotId()).isEqualTo("snap-2026-q4-v1");
        assertThat(job.getTimeLimitSeconds()).isEqualTo(20);
    }
}
