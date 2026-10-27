package org.soa8.freight.declaration.repository;

import org.soa8.freight.declaration.model.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, String> {
}
