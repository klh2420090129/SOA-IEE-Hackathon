package org.soa8.freight.compartment.repository;

import org.soa8.freight.compartment.model.CandidateLayoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CandidateLayoutRepository extends JpaRepository<CandidateLayoutEntity, String> {
    Optional<CandidateLayoutEntity> findByAssessmentIdAndLayoutVersion(String assessmentId, int layoutVersion);
}
