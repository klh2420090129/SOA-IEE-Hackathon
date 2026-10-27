package org.soa8.freight.compartment.repository;

import org.soa8.freight.compartment.model.AssessmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<AssessmentEntity, String> {
}
