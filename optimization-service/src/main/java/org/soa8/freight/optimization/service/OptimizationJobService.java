package org.soa8.freight.optimization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.soa8.freight.common.model.*;
import org.soa8.freight.common.util.RuleSnapshotDigestUtils;
import org.soa8.freight.optimization.model.*;
import org.soa8.freight.optimization.repository.OptimizationJobRepository;
import org.soa8.freight.optimization.repository.OptimizationOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing the authoritative lifecycle of Optimization Jobs.
 */
@Service
public class OptimizationJobService {
    private static final Logger log = LoggerFactory.getLogger(OptimizationJobService.class);

    private final OptimizationJobRepository jobRepository;
    private final OptimizationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.command-topic:soa.s06.t08.optimization.commands}")
    private String commandTopic;

    public OptimizationJobService(OptimizationJobRepository jobRepository,
                                  OptimizationOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Atomically creates the job record in status SUBMITTED and enqueues the dispatch command
     * into the transactional outbox table within the same DB transaction.
     */
    @Transactional
    public OptimizationJobEntity submitJobDurable(String declarationId,
                                                  CompartmentDto compartment,
                                                  List<CargoItemDto> items,
                                                  RuleSnapshotDto ruleSnapshot,
                                                  String ruleSnapshotId,
                                                  String algorithm,
                                                  int timeLimitSeconds) {
        String jobId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        OptimizationJobEntity job = new OptimizationJobEntity(
                jobId, declarationId, compartment.getCompartmentId(), ruleSnapshotId
        );
        job.setTimeLimitSeconds(timeLimitSeconds);
        job.setCorrelationId(correlationId);
        job.setProvenance(algorithm != null ? algorithm : "HEURISTIC_EP_3D_V1");
        job.setStatus(JobStatus.SUBMITTED);
        jobRepository.save(job);

        // Compute canonical rule snapshot digest if snapshot provided
        String snapshotDigest = null;
        if (ruleSnapshot != null) {
            snapshotDigest = RuleSnapshotDigestUtils.computeCanonicalDigest(ruleSnapshot);
        }

        // Construct versioned dispatch command
        String commandId = UUID.randomUUID().toString();
        OptimizationCommandDto command = new OptimizationCommandDto(
                commandId,
                jobId,
                job.getCurrentAttemptId(),
                correlationId,
                ruleSnapshotId,
                snapshotDigest,
                ruleSnapshot,
                declarationId,
                items,
                compartment,
                job.getProvenance(),
                timeLimitSeconds,
                Map.of("createdVia", "OptimizationJobService")
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(command);
            OptimizationOutboxEntity outboxEntry = new OptimizationOutboxEntity(
                    commandId,
                    "OptimizationCommandDispatched",
                    jobId,
                    "OptimizationJob",
                    commandTopic,
                    payloadJson
            );
            outboxRepository.save(outboxEntry);
            log.info("Persisted durable job [{}] and outbox command [{}] to topic [{}]",
                    jobId, commandId, commandTopic);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize optimization command payload for job " + jobId, e);
        }

        return job;
    }

    /**
     * Mark job running upon acknowledged worker uptake.
     */
    @Transactional
    public void markJobRunning(String jobId, String attemptId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getCurrentAttemptId() != null && job.getCurrentAttemptId().equals(attemptId)) {
                if (job.getStatus() == JobStatus.SUBMITTED) {
                    job.transitionTo(JobStatus.RUNNING);
                    job.setStartedAt(Instant.now());
                    jobRepository.save(job);
                    log.info("Job [{}] attempt [{}] transitioned to RUNNING", jobId, attemptId);
                }
            }
        });
    }

    /**
     * Mark job failed with structured error message.
     */
    @Transactional
    public void markJobFailed(String jobId, String attemptId, String errorMessage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getCurrentAttemptId() != null && job.getCurrentAttemptId().equals(attemptId)) {
                if (!job.getStatus().isTerminal()) {
                    job.transitionTo(JobStatus.FAILED);
                    job.setErrorMessage(errorMessage);
                    job.setCompletedAt(Instant.now());
                    jobRepository.save(job);
                    log.warn("Job [{}] attempt [{}] transitioned to FAILED: {}", jobId, attemptId, errorMessage);
                }
            }
        });
    }

    /**
     * Scheduled or manual recovery mechanism for stuck jobs beyond execution deadline.
     * Detects jobs in SUBMITTED or RUNNING status whose deadline (startedAt/createdAt + timeLimitSeconds)
     * has elapsed. Transitions them to terminal TIMEOUT status with concurrency safety.
     *
     * @return Number of jobs transitioned to TIMEOUT
     */
    @Transactional
    public int recoverStuckJobs() {
        Instant now = Instant.now();
        List<OptimizationJobEntity> activeJobs = jobRepository.findByStatusIn(List.of(JobStatus.SUBMITTED, JobStatus.RUNNING));
        int timedOutCount = 0;

        for (OptimizationJobEntity job : activeJobs) {
            int budgetSeconds = (job.getTimeLimitSeconds() != null && job.getTimeLimitSeconds() > 0)
                    ? job.getTimeLimitSeconds() : 20;

            // Anchor deadline calculation to startedAt if running, or createdAt if submitted
            Instant startAnchor = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
            if (startAnchor == null) {
                startAnchor = now;
            }

            Instant deadline = startAnchor.plusSeconds(budgetSeconds);

            if (now.isAfter(deadline)) {
                // Ensure atomic check-and-set: verify still in active non-terminal state
                if (job.getStatus() == JobStatus.SUBMITTED || job.getStatus() == JobStatus.RUNNING) {
                    log.warn("Job [{}] attempt [{}] exceeded deadline (budget={}s, elapsed={}s). Transitioning to TIMEOUT.",
                            job.getJobId(), job.getCurrentAttemptId(), budgetSeconds,
                            java.time.Duration.between(startAnchor, now).toSeconds());

                    job.transitionTo(JobStatus.TIMEOUT);
                    job.setSolverStatus(SolverStatus.TIMEOUT.name());
                    job.setErrorMessage("Optimization execution exceeded allocated solver budget of " + budgetSeconds + " seconds");
                    job.setCompletedAt(now);
                    jobRepository.save(job);
                    timedOutCount++;
                }
            }
        }
        return timedOutCount;
    }
}

