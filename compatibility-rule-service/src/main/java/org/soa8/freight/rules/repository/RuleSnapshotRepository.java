package org.soa8.freight.rules.repository;

import org.soa8.freight.rules.model.RuleSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleSnapshotRepository extends JpaRepository<RuleSnapshotEntity, String> {
}
