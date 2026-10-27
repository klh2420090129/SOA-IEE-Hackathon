package org.soa8.freight.optimization.repository;

import org.soa8.freight.optimization.model.OptimizationJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptimizationJobRepository extends JpaRepository<OptimizationJobEntity, String> {
}
