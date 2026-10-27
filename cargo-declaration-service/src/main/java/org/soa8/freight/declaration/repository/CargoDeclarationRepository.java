package org.soa8.freight.declaration.repository;

import org.soa8.freight.declaration.model.CargoDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CargoDeclarationRepository extends JpaRepository<CargoDeclaration, String> {
    List<CargoDeclaration> findByShipperId(String shipperId);
}
