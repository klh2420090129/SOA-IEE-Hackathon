package org.soa8.freight.optimization.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled background recovery service that sweeps for jobs remaining SUBMITTED
 * or RUNNING beyond their configured execution deadline.
 */
@Service
public class OptimizationTimeoutRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(OptimizationTimeoutRecoveryService.class);

    private final OptimizationJobService optimizationJobService;

    public OptimizationTimeoutRecoveryService(OptimizationJobService optimizationJobService) {
        this.optimizationJobService = optimizationJobService;
    }

    @Scheduled(fixedDelayString = "${app.optimization.timeout-sweep-delay-ms:3000}")
    public void sweepTimedOutJobs() {
        try {
            int recovered = optimizationJobService.recoverStuckJobs();
            if (recovered > 0) {
                log.info("OptimizationTimeoutRecoveryService recovered {} timed-out jobs", recovered);
            }
        } catch (Exception e) {
            log.error("Error during scheduled optimization timeout sweep: {}", e.getMessage(), e);
        }
    }
}
