package org.soa8.freight.compartment.repository;

import org.soa8.freight.compartment.model.AssessmentDecisionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssessmentDecisionAuditRepository extends JpaRepository<AssessmentDecisionAuditEntity, String> {
    List<AssessmentDecisionAuditEntity> findByAssessmentIdOrderByDecidedAtAsc(String assessmentId);
    List<AssessmentDecisionAuditEntity> findByDeclarationIdOrderByDecidedAtAsc(String declarationId);
}
