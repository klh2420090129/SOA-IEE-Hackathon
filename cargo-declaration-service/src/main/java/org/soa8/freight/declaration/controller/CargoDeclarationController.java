package org.soa8.freight.declaration.controller;

import org.soa8.freight.declaration.model.CargoDeclaration;
import org.soa8.freight.declaration.model.CargoItemEntity;
import org.soa8.freight.declaration.model.DeclarationWorkflowProjection;
import org.soa8.freight.declaration.repository.CargoDeclarationRepository;
import org.soa8.freight.declaration.repository.DeclarationWorkflowProjectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.jsonwebtoken.Claims;
import org.soa8.freight.declaration.security.TokenValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final TokenValidator tokenValidator;

    public CargoDeclarationController(CargoDeclarationRepository declarationRepository,
                                      DeclarationWorkflowProjectionRepository projectionRepository,
                                      @org.springframework.beans.factory.annotation.Qualifier("declarationTokenValidator") TokenValidator tokenValidator) {
        this.declarationRepository = declarationRepository;
        this.projectionRepository = projectionRepository;
        this.tokenValidator = tokenValidator;
    }

    @GetMapping
    public ResponseEntity<?> listDeclarations(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "shipperId", required = false) String filterShipperId) {
        Claims claims;
        try {
            claims = tokenValidator.validateToken(authHeader);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "Valid bearer token required: " + e.getMessage()
            ));
        }

        // Validate and normalize pagination bounds
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size)); // max 100 per page
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        boolean isPlanner = tokenValidator.hasRole(claims, "ROLE_LOAD_PLANNER");
        boolean isShipper = tokenValidator.hasRole(claims, "ROLE_SHIPPER");

        if (!isPlanner && !isShipper) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", "Access denied: insufficient privileges to list cargo declarations"
            ));
        }

        Page<CargoDeclaration> resultPage;
        String normalizedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        if (isPlanner) {
            // Load Planner has global visibility across all tenants
            if (filterShipperId != null && !filterShipperId.isBlank()) {
                if (normalizedStatus != null) {
                    resultPage = declarationRepository.findByShipperIdAndStatus(filterShipperId.trim(), normalizedStatus, pageable);
                } else {
                    resultPage = declarationRepository.findByShipperId(filterShipperId.trim(), pageable);
                }
            } else {
                if (normalizedStatus != null) {
                    resultPage = declarationRepository.findByStatus(normalizedStatus, pageable);
                } else {
                    resultPage = declarationRepository.findAll(pageable);
                }
            }
        } else {
            // Shipper tenant isolation: strictly enforce authenticated caller subject
            String authenticatedShipper = claims.getSubject();
            if (normalizedStatus != null) {
                resultPage = declarationRepository.findByShipperIdAndStatus(authenticatedShipper, normalizedStatus, pageable);
            } else {
                resultPage = declarationRepository.findByShipperId(authenticatedShipper, pageable);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", resultPage.getContent());
        response.put("page", resultPage.getNumber());
        response.put("size", resultPage.getSize());
        response.put("totalElements", resultPage.getTotalElements());
        response.put("totalPages", resultPage.getTotalPages());
        response.put("first", resultPage.isFirst());
        response.put("last", resultPage.isLast());

        return ResponseEntity.ok(response);
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
    public ResponseEntity<?> getDeclaration(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id) {
        Claims claims;
        try {
            claims = tokenValidator.validateToken(authHeader);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "Valid bearer token required: " + e.getMessage()
            ));
        }

        boolean isPlanner = tokenValidator.hasRole(claims, "ROLE_LOAD_PLANNER");
        boolean isShipper = tokenValidator.hasRole(claims, "ROLE_SHIPPER");

        if (!isPlanner && !isShipper) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", "Access denied: insufficient privileges to access cargo declaration"
            ));
        }

        Optional<CargoDeclaration> declOpt = declarationRepository.findById(id);
        if (declOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "DECLARATION_NOT_FOUND",
                    "message", "Declaration not found with id: " + id
            ));
        }

        CargoDeclaration declaration = declOpt.get();

        if (isShipper && !isPlanner) {
            String authenticatedShipper = claims.getSubject();
            if (!authenticatedShipper.equals(declaration.getShipperId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "code", "FORBIDDEN",
                        "message", "Cross-tenant access denied: you cannot access another shipper's cargo declaration"
                ));
            }
        }

        return ResponseEntity.ok(declaration);
    }

    @GetMapping("/{id}/workflow")
    public ResponseEntity<?> getWorkflowProjection(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id) {
        Claims claims;
        try {
            claims = tokenValidator.validateToken(authHeader);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "Valid bearer token required: " + e.getMessage()
            ));
        }

        boolean isPlanner = tokenValidator.hasRole(claims, "ROLE_LOAD_PLANNER");
        boolean isShipper = tokenValidator.hasRole(claims, "ROLE_SHIPPER");

        if (!isPlanner && !isShipper) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", "Access denied: insufficient privileges to access cargo declaration workflow projection"
            ));
        }

        Optional<CargoDeclaration> declOpt = declarationRepository.findById(id);
        if (declOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "DECLARATION_NOT_FOUND",
                    "message", "Declaration not found with id: " + id
            ));
        }

        CargoDeclaration declaration = declOpt.get();

        if (isShipper && !isPlanner) {
            String authenticatedShipper = claims.getSubject();
            if (!authenticatedShipper.equals(declaration.getShipperId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "code", "FORBIDDEN",
                        "message", "Cross-tenant access denied: you cannot access another shipper's cargo declaration workflow projection"
                ));
            }
        }

        Optional<DeclarationWorkflowProjection> projection = projectionRepository.findById(id);
        if (projection.isPresent()) {
            return ResponseEntity.ok(projection.get());
        }

        DeclarationWorkflowProjection fallback = new DeclarationWorkflowProjection(
                id, null, "SUBMITTED", null, null
        );
        return ResponseEntity.ok(fallback);
    }
}
