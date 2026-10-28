package org.soa8.freight.declaration.controller;

import io.jsonwebtoken.Claims;
import org.soa8.freight.declaration.model.CargoTemplate;
import org.soa8.freight.declaration.model.CargoTemplateItemEntity;
import org.soa8.freight.declaration.repository.CargoTemplateRepository;
import org.soa8.freight.declaration.security.TokenValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cargo-templates")
public class CargoTemplateController {

    private final CargoTemplateRepository templateRepository;
    private final TokenValidator tokenValidator;

    private static final Pattern UN_NUMBER_PATTERN = Pattern.compile("^UN\\d{4}$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> VALID_HAZARD_CLASSES = Set.of(
            "1", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6",
            "2.1", "2.2", "2.3",
            "3",
            "4.1", "4.2", "4.3",
            "5.1", "5.2",
            "6.1", "6.2",
            "7",
            "8",
            "9"
    );

    public CargoTemplateController(CargoTemplateRepository templateRepository, TokenValidator tokenValidator) {
        this.templateRepository = templateRepository;
        this.tokenValidator = tokenValidator;
    }

    public static class TemplateItemRequest {
        public String cargoItemId;
        public String description;
        public String hazardClass;
        public String unNumber;
        public Integer lengthMm;
        public Integer widthMm;
        public Integer heightMm;
        public Double weightKg;
        public Boolean stackable;
        public Double maxStackWeightKg;
        public Integer quantity;
    }

    public static class SaveTemplateRequest {
        public String name;
        public String description;
        public List<TemplateItemRequest> items;
    }

    public static class UpdateTemplateRequest {
        public String name;
        public String description;
        public List<TemplateItemRequest> items;
    }

    public static class InsufficientRoleException extends RuntimeException {
        public InsufficientRoleException(String message) {
            super(message);
        }
    }

    private Claims authenticateAndAuthorizeShipper(String authHeader) {
        Claims claims = tokenValidator.validateToken(authHeader);
        if (!tokenValidator.hasRole(claims, "ROLE_SHIPPER")) {
            throw new InsufficientRoleException("User does not have ROLE_SHIPPER authority");
        }
        return claims;
    }

    @PostMapping
    public ResponseEntity<?> createTemplate(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody SaveTemplateRequest req) {
        Claims claims;
        try {
            claims = authenticateAndAuthorizeShipper(authHeader);
        } catch (InsufficientRoleException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", e.getMessage()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_TOKEN",
                    "message", "Token expired or invalid: " + e.getMessage()
            ));
        }

        String shipperId = claims.getSubject();

        // Validate name
        if (req.name == null || req.name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", "Template name is required and cannot be blank"
            ));
        }

        // Validate items
        if (req.items == null || req.items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", "At least one cargo item is required in a template"
            ));
        }

        List<String> validationErrors = new ArrayList<>();
        List<CargoTemplateItemEntity> entities = validateAndConvertItems(req.items, validationErrors);

        if (!validationErrors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", "Cargo item validation failed",
                    "errors", validationErrors
            ));
        }

        CargoTemplate template = new CargoTemplate(
                UUID.randomUUID().toString(),
                shipperId,
                req.name.trim(),
                req.description != null ? req.description.trim() : null
        );
        template.setItems(entities);

        CargoTemplate saved = templateRepository.save(template);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<?> listTemplates(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims;
        try {
            claims = authenticateAndAuthorizeShipper(authHeader);
        } catch (InsufficientRoleException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", e.getMessage()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_TOKEN",
                    "message", "Token expired or invalid: " + e.getMessage()
            ));
        }

        String shipperId = claims.getSubject();
        List<CargoTemplate> list = templateRepository.findByShipperIdOrderByCreatedAtDesc(shipperId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTemplate(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id) {
        Claims claims;
        try {
            claims = authenticateAndAuthorizeShipper(authHeader);
        } catch (InsufficientRoleException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", e.getMessage()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_TOKEN",
                    "message", "Token expired or invalid: " + e.getMessage()
            ));
        }

        String shipperId = claims.getSubject();
        return templateRepository.findByIdAndShipperId(id, shipperId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "TEMPLATE_NOT_FOUND",
                        "message", "Cargo template not found: " + id
                )));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTemplate(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody UpdateTemplateRequest req) {
        Claims claims;
        try {
            claims = authenticateAndAuthorizeShipper(authHeader);
        } catch (InsufficientRoleException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", e.getMessage()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_TOKEN",
                    "message", "Token expired or invalid: " + e.getMessage()
            ));
        }

        String shipperId = claims.getSubject();
        Optional<CargoTemplate> existingOpt = templateRepository.findByIdAndShipperId(id, shipperId);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "TEMPLATE_NOT_FOUND",
                    "message", "Cargo template not found: " + id
            ));
        }

        CargoTemplate template = existingOpt.get();

        if (req.name != null) {
            if (req.name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", "VALIDATION_ERROR",
                        "message", "Template name cannot be blank"
                ));
            }
            template.setName(req.name.trim());
        }

        if (req.description != null) {
            template.setDescription(req.description.trim());
        }

        if (req.items != null) {
            if (req.items.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", "VALIDATION_ERROR",
                        "message", "Template cannot have an empty item list"
                ));
            }
            List<String> validationErrors = new ArrayList<>();
            List<CargoTemplateItemEntity> entities = validateAndConvertItems(req.items, validationErrors);
            if (!validationErrors.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", "VALIDATION_ERROR",
                        "message", "Cargo item validation failed",
                        "errors", validationErrors
                ));
            }
            template.getItems().clear();
            template.getItems().addAll(entities);
        }

        template.setUpdatedAt(Instant.now());
        CargoTemplate saved = templateRepository.save(template);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTemplate(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id) {
        Claims claims;
        try {
            claims = authenticateAndAuthorizeShipper(authHeader);
        } catch (InsufficientRoleException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", e.getMessage()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_TOKEN",
                    "message", "Token expired or invalid: " + e.getMessage()
            ));
        }

        String shipperId = claims.getSubject();
        Optional<CargoTemplate> existingOpt = templateRepository.findByIdAndShipperId(id, shipperId);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "TEMPLATE_NOT_FOUND",
                    "message", "Cargo template not found: " + id
            ));
        }

        templateRepository.delete(existingOpt.get());
        return ResponseEntity.noContent().build();
    }

    private List<CargoTemplateItemEntity> validateAndConvertItems(List<TemplateItemRequest> items, List<String> errors) {
        List<CargoTemplateItemEntity> entities = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {
            TemplateItemRequest it = items.get(i);
            int rowNum = i + 1;

            if (it.description == null || it.description.trim().isEmpty()) {
                errors.add("Item " + rowNum + ": description is required");
            }

            if (it.hazardClass == null || !VALID_HAZARD_CLASSES.contains(it.hazardClass.trim())) {
                errors.add("Item " + rowNum + ": unrecognized statutory hazard class '" + it.hazardClass + "'");
            }

            if (it.unNumber == null || !UN_NUMBER_PATTERN.matcher(it.unNumber.trim()).matches()) {
                errors.add("Item " + rowNum + ": invalid UN number format '" + it.unNumber + "' (expected format UN####)");
            }

            if (it.lengthMm == null || it.lengthMm <= 0) {
                errors.add("Item " + rowNum + ": lengthMm must be a positive integer in mm");
            }
            if (it.widthMm == null || it.widthMm <= 0) {
                errors.add("Item " + rowNum + ": widthMm must be a positive integer in mm");
            }
            if (it.heightMm == null || it.heightMm <= 0) {
                errors.add("Item " + rowNum + ": heightMm must be a positive integer in mm");
            }

            if (it.weightKg == null || it.weightKg <= 0) {
                errors.add("Item " + rowNum + ": weightKg must be greater than 0 kg");
            }

            if (it.quantity == null || it.quantity < 1) {
                errors.add("Item " + rowNum + ": quantity must be an integer >= 1");
            }

            String cargoItemId = it.cargoItemId != null && !it.cargoItemId.trim().isEmpty()
                    ? it.cargoItemId.trim()
                    : UUID.randomUUID().toString();

            if (seenIds.contains(cargoItemId.toLowerCase())) {
                errors.add("Item " + rowNum + ": duplicate cargoItemId '" + cargoItemId + "' within template");
            }
            seenIds.add(cargoItemId.toLowerCase());

            entities.add(new CargoTemplateItemEntity(
                    UUID.randomUUID().toString(),
                    cargoItemId,
                    it.description != null ? it.description.trim() : "",
                    it.hazardClass != null ? it.hazardClass.trim() : "",
                    it.unNumber != null ? it.unNumber.trim().toUpperCase() : "",
                    it.lengthMm != null ? it.lengthMm : 0,
                    it.widthMm != null ? it.widthMm : 0,
                    it.heightMm != null ? it.heightMm : 0,
                    it.weightKg != null ? it.weightKg : 0.0,
                    Boolean.TRUE.equals(it.stackable),
                    it.maxStackWeightKg != null ? it.maxStackWeightKg : 0.0,
                    it.quantity != null && it.quantity > 0 ? it.quantity : 1
            ));
        }

        return entities;
    }
}
