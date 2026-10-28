package org.soa8.freight.declaration.repository;

import org.soa8.freight.declaration.model.CargoDeclaration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CargoDeclarationRepository extends JpaRepository<CargoDeclaration, String> {
    List<CargoDeclaration> findByShipperId(String shipperId);
    Page<CargoDeclaration> findByShipperId(String shipperId, Pageable pageable);
    Page<CargoDeclaration> findByShipperIdAndStatus(String shipperId, String status, Pageable pageable);
    Page<CargoDeclaration> findByStatus(String status, Pageable pageable);
}
