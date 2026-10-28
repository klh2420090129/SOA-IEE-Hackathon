package org.soa8.freight.declaration.repository;

import org.soa8.freight.declaration.model.CargoTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CargoTemplateRepository extends JpaRepository<CargoTemplate, String> {

    List<CargoTemplate> findByShipperIdOrderByCreatedAtDesc(String shipperId);

    Optional<CargoTemplate> findByIdAndShipperId(String id, String shipperId);
}
