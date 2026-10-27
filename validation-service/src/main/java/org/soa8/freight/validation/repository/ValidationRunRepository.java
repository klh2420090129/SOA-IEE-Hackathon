package org.soa8.freight.validation.repository;

import org.soa8.freight.validation.model.ValidationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationRunRepository extends JpaRepository<ValidationRunEntity, String> {
}
