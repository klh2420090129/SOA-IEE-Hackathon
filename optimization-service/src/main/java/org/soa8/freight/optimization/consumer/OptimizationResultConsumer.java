package org.soa8.freight.optimization.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.soa8.freight.common.model.*;
import org.soa8.freight.optimization.model.*;
import org.soa8.freight.optimization.repository.OptimizationJobRepository;
import org.soa8.freight.optimization.repository.ProcessedMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Idempotent Kafka consumer for worker optimization results.
 * Topic: soa.s06.t08.optimization.results
 */
@Component
public class OptimizationResultConsumer {
    private static final Logger log = LoggerFactory.getLogger(OptimizationResultConsumer.class);

    private final OptimizationJobRepository jobRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.dead-letter-topic:soa.s06.t08.optimization.dlt}")
    private String deadLetterTopic;

    public OptimizationResultConsumer(OptimizationJobRepository jobRepository,
                                      ProcessedMessageRepository processedMessageRepository,
                                      ObjectMapper objectMapper,
                                      KafkaTemplate<String, String> kafkaTemplate) {
        this.jobRepository = jobRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "${app.kafka.result-topic:soa.s06.t08.optimization.results}",
            groupId = "${spring.kafka.consumer.group-id:optimization-service-group}"
    )
    @Transactional
    public void consumeOptimizationResult(String payload) {
        log.info("Received optimization result payload ({} bytes)", payload != null ? payload.length() : 0);

        OptimizationResultDto resultDto;
        try {
            resultDto = objectMapper.readValue(payload, OptimizationResultDto.class);
        } catch (Exception e) {
            log.error("Failed to parse optimization result payload. Routing to DLT: {}", payload, e);
            sendToDeadLetterQueue("PARSE_ERROR", payload, e.getMessage());
            return;
        }

        // Schema and mandatory field validation
        if (resultDto.getJobId() == null || resultDto.getJobId().isBlank()
                || resultDto.getAttemptId() == null || resultDto.getAttemptId().isBlank()
                || resultDto.getResultId() == null || resultDto.getResultId().isBlank()) {
            log.error("Rejecting malformed optimization result: missing mandatory jobId/attemptId/resultId");
            sendToDeadLetterQueue("VALIDATION_ERROR", payload, "Missing mandatory fields (jobId/attemptId/resultId)");
            return;
        }

        // Validate solverStatus is present and a valid SolverStatus enum value
        String rawSolverStatus = resultDto.getSolverStatus();
        if (rawSolverStatus == null || rawSolverStatus.isBlank()) {
            log.error("Rejecting malformed optimization result for job [{}]: missing mandatory solverStatus", resultDto.getJobId());
            sendToDeadLetterQueue("VALIDATION_ERROR", payload, "Missing mandatory solverStatus");
            return;
        }

        SolverStatus parsedSolverStatus;
        try {
            parsedSolverStatus = SolverStatus.valueOf(rawSolverStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Rejecting malformed optimization result for job [{}]: unrecognized solverStatus [{}]",
                    resultDto.getJobId(), rawSolverStatus);
            sendToDeadLetterQueue("VALIDATION_ERROR", payload, "Invalid solverStatus value: " + rawSolverStatus);
            return;
        }

        // Deduplication: check if this result has already been processed
        String dedupKey = resultDto.getResultId();
        if (processedMessageRepository.existsById(dedupKey)) {
            log.warn("Duplicate result [{}] for job [{}] attempt [{}] already processed. Skipping.",
                    resultDto.getResultId(), resultDto.getJobId(), resultDto.getAttemptId());
            return;
        }

        Optional<OptimizationJobEntity> jobOpt = jobRepository.findById(resultDto.getJobId());
        if (jobOpt.isEmpty()) {
            log.error("Received result for unknown jobId [{}]. Routing to DLT.", resultDto.getJobId());
            sendToDeadLetterQueue("UNKNOWN_JOB", payload, "Job ID not found in repository");
            return;
        }

        OptimizationJobEntity job = jobOpt.get();

        // Stale attempt check
        if (!resultDto.getAttemptId().equals(job.getCurrentAttemptId())) {
            log.warn("Rejecting stale attempt result [{}] for job [{}]. Current active attempt is [{}].",
                    resultDto.getAttemptId(), job.getJobId(), job.getCurrentAttemptId());
            // Record deduplication so we don't re-log repeatedly
            processedMessageRepository.save(new ProcessedMessageEntity(dedupKey, job.getJobId(), resultDto.getAttemptId()));
            return;
        }

        // Check if job is already in terminal state
        if (job.getStatus().isTerminal()) {
            log.warn("Job [{}] is already in terminal state [{}]. Ignoring result [{}].",
                    job.getJobId(), job.getStatus(), resultDto.getResultId());
            processedMessageRepository.save(new ProcessedMessageEntity(dedupKey, job.getJobId(), resultDto.getAttemptId()));
            return;
        }

        // Rule snapshot mismatch check
        if (job.getRuleSnapshotId() != null && resultDto.getRuleSnapshotId() != null) {
            if (!job.getRuleSnapshotId().equalsIgnoreCase(resultDto.getRuleSnapshotId())) {
                log.error("Snapshot identity mismatch for job [{}]: expected [{}], worker used [{}]",
                        job.getJobId(), job.getRuleSnapshotId(), resultDto.getRuleSnapshotId());
                job.transitionTo(JobStatus.FAILED);
                job.setErrorMessage("Rule snapshot mismatch: expected " + job.getRuleSnapshotId() + ", worker used " + resultDto.getRuleSnapshotId());
                job.setCompletedAt(Instant.now());
                jobRepository.save(job);
                processedMessageRepository.save(new ProcessedMessageEntity(dedupKey, job.getJobId(), resultDto.getAttemptId()));
                return;
            }
        }

        // Build CandidateLayoutDto from result
        CandidateLayoutDto candidateLayout = new CandidateLayoutDto(
                "cand-" + job.getJobId() + "-" + job.getAttemptCount(),
                1,
                job.getCompartmentId(),
                resultDto.getContentDigest() != null ? resultDto.getContentDigest() : "worker-digest",
                parsedSolverStatus.name(),
                resultDto.getProvenance() != null ? resultDto.getProvenance() : "PYTHON_WORKER",
                resultDto.getPlacements() != null ? resultDto.getPlacements() : java.util.List.of()
        );
        candidateLayout.setUnassignedItems(resultDto.getUnassignedItems() != null ? resultDto.getUnassignedItems() : java.util.List.of());
        candidateLayout.setRuleSnapshotId(job.getRuleSnapshotId());
        candidateLayout.setExecutionDurationMs(resultDto.getExecutionDurationMs() != null ? resultDto.getExecutionDurationMs() : 0);
        try {
            candidateLayout.setDiagnostics(resultDto.getDiagnostics() != null ? objectMapper.writeValueAsString(resultDto.getDiagnostics()) : "{}");
        } catch (Exception e) {
            candidateLayout.setDiagnostics("{}");
        }

        try {
            String candidateJson = objectMapper.writeValueAsString(candidateLayout);
            job.setResultJson(candidateJson);
            job.setSolverStatus(parsedSolverStatus.name());
            job.setProvenance(resultDto.getProvenance() != null ? resultDto.getProvenance() : "PYTHON_WORKER");
            job.setExecutionDurationMs(resultDto.getExecutionDurationMs());

            if (parsedSolverStatus == SolverStatus.TIMEOUT) {
                job.transitionTo(JobStatus.TIMEOUT);
            } else {
                job.transitionTo(JobStatus.COMPLETED);
            }
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            // Persist message deduplication key in same transaction
            processedMessageRepository.save(new ProcessedMessageEntity(dedupKey, job.getJobId(), resultDto.getAttemptId()));
            log.info("Successfully accepted and persisted worker optimization result [{}] for job [{}] attempt [{}] (solverStatus={})",
                    resultDto.getResultId(), job.getJobId(), resultDto.getAttemptId(), resultDto.getSolverStatus());

        } catch (Exception e) {
            log.error("Failed to serialize candidate layout for job [{}]", job.getJobId(), e);
            job.transitionTo(JobStatus.FAILED);
            job.setErrorMessage("Internal serialization error accepting result: " + e.getMessage());
            jobRepository.save(job);
        }
    }

    private void sendToDeadLetterQueue(String reason, String payload, String error) {
        try {
            String dltMessage = objectMapper.writeValueAsString(Map.of(
                    "reason", reason,
                    "error", error != null ? error : "",
                    "timestamp", Instant.now().toString(),
                    "payload", payload != null ? payload : ""
            ));
            kafkaTemplate.send(deadLetterTopic, dltMessage);
            log.info("Dispatched poison message to DLT topic [{}]", deadLetterTopic);
        } catch (Exception ex) {
            log.error("Failed to forward poison message to DLT: {}", ex.getMessage());
        }
    }
}
