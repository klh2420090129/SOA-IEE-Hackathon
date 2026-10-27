package org.soa8.freight.optimization.repository;

import org.soa8.freight.optimization.model.ProcessedMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, String> {
}
