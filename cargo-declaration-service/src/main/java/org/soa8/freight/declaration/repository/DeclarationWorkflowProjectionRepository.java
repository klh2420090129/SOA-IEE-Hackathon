package org.soa8.freight.declaration.repository;

import org.soa8.freight.declaration.model.DeclarationWorkflowProjection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeclarationWorkflowProjectionRepository extends JpaRepository<DeclarationWorkflowProjection, String> {
}
