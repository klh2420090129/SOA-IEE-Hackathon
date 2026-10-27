package org.soa8.freight.declaration.controller;

import org.soa8.freight.declaration.model.CargoDeclaration;
import org.soa8.freight.declaration.model.CargoItemEntity;
import org.soa8.freight.declaration.model.DeclarationWorkflowProjection;
import org.soa8.freight.declaration.repository.CargoDeclarationRepository;
import org.soa8.freight.declaration.repository.DeclarationWorkflowProjectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/declarations")
public class CargoDeclarationController {

    private final CargoDeclarationRepository declarationRepository;
    private final DeclarationWorkflowProjectionRepository projectionRepository;

    public CargoDeclarationController(CargoDeclarationRepository declarationRepository,
                                      DeclarationWorkflowProjectionRepository projectionRepository) {
        this.declarationRepository = declarationRepository;
        this.projectionRepository = projectionRepository;
    }

    public static class CreateDeclarationRequest {
        public String referenceNumber;
        public String shipperId;
        public String origin;
        public String destination;
        public List<ItemRequest> items;
    }

    public static class ItemRequest {
        public String cargoItemId;
        public String description;
        public String hazardClass;
        public String unNumber;
        public int lengthMm;
        public int widthMm;
        public int heightMm;
        public double weightKg;
        public boolean stackable;
        public double maxStackWeightKg;
        public int quantity;
    }

    @PostMapping
    public ResponseEntity<?> createDeclaration(@RequestBody CreateDeclarationRequest req) {
        if (req.referenceNumber == null || req.shipperId == null || req.items == null || req.items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", "referenceNumber, shipperId, and at least one item are required"
            ));
        }

        CargoDeclaration declaration = new CargoDeclaration(
                UUID.randomUUID().toString(),
                req.referenceNumber,
                req.shipperId,
                req.origin,
                req.destination
        );

        List<CargoItemEntity> items = req.items.stream().map(it -> new CargoItemEntity(
                it.cargoItemId != null ? it.cargoItemId : UUID.randomUUID().toString(),
                it.description,
                it.hazardClass,
                it.unNumber,
                it.lengthMm,
                it.widthMm,
                it.heightMm,
                it.weightKg,
                it.stackable,
                it.maxStackWeightKg,
                it.quantity > 0 ? it.quantity : 1
        )).collect(Collectors.toList());

        declaration.setItems(items);
        CargoDeclaration saved = declarationRepository.save(declaration);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDeclaration(@PathVariable("id") String id) {
        return declarationRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "DECLARATION_NOT_FOUND",
                        "message", "Declaration not found with id: " + id
                )));
    }

    @GetMapping("/{id}/workflow")
    public ResponseEntity<?> getWorkflowProjection(@PathVariable("id") String id) {
        Optional<DeclarationWorkflowProjection> projection = projectionRepository.findById(id);
        if (projection.isPresent()) {
            return ResponseEntity.ok(projection.get());
        }

        // Check if declaration exists
        if (declarationRepository.existsById(id)) {
            DeclarationWorkflowProjection fallback = new DeclarationWorkflowProjection(
                    id, null, "SUBMITTED", null, null
            );
            return ResponseEntity.ok(fallback);
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "DECLARATION_NOT_FOUND",
                "message", "Declaration not found with id: " + id
        ));
    }
}
