package org.soa8.freight.optimization.repository;

import org.soa8.freight.optimization.model.OptimizationOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OptimizationOutboxRepository extends JpaRepository<OptimizationOutboxEntity, String> {
    List<OptimizationOutboxEntity> findByStatusOrderByCreatedAtAsc(String status);
}
