package org.soa8.freight.compartment.repository;

import org.soa8.freight.compartment.model.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(String status);
}
