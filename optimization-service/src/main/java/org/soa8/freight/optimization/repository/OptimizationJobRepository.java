package org.soa8.freight.optimization.repository;

import org.soa8.freight.optimization.model.JobStatus;
import org.soa8.freight.optimization.model.OptimizationJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OptimizationJobRepository extends JpaRepository<OptimizationJobEntity, String> {
    List<OptimizationJobEntity> findByStatusIn(Collection<JobStatus> statuses);
}

