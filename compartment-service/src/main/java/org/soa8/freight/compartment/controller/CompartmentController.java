package org.soa8.freight.compartment.controller;

import org.soa8.freight.common.model.CandidateLayoutDto;
import org.soa8.freight.common.model.CompartmentDto;
import org.soa8.freight.common.model.ValidationResultDto;
import org.soa8.freight.compartment.model.AssessmentEntity;
import org.soa8.freight.compartment.model.CandidateLayoutEntity;
import org.soa8.freight.compartment.repository.AssessmentRepository;
import org.soa8.freight.compartment.repository.CandidateLayoutRepository;
import org.soa8.freight.compartment.repository.OutboxEventRepository;
import org.soa8.freight.compartment.service.AssessmentWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.soa8.freight.compartment.security.TokenValidator;
import io.jsonwebtoken.Claims;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CompartmentController {

    private final AssessmentRepository assessmentRepository;
    private final CandidateLayoutRepository layoutRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final org.soa8.freight.compartment.repository.AssessmentDecisionAuditRepository decisionAuditRepository;
    private final AssessmentWorkflowService workflowService;
    private final TokenValidator tokenValidator;

    public CompartmentController(AssessmentRepository assessmentRepository,
                                 CandidateLayoutRepository layoutRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 org.soa8.freight.compartment.repository.AssessmentDecisionAuditRepository decisionAuditRepository,
                                 AssessmentWorkflowService workflowService,
                                 @org.springframework.beans.factory.annotation.Qualifier("compartmentTokenValidator") TokenValidator tokenValidator) {
        this.assessmentRepository = assessmentRepository;
        this.layoutRepository = layoutRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.decisionAuditRepository = decisionAuditRepository;
        this.workflowService = workflowService;
        this.tokenValidator = tokenValidator;
    }

    public static class CreateAssessmentRequest {
        public String declarationId;
        public int declarationVersion;
        public String ruleSnapshotId;
        public CompartmentDto compartment;
    }

    public static class DecisionRequest {
        public int layoutVersion;
        public String expectedDigest;
        public String plannerId;
        public String rejectionReasonCode;
        public String notes;
    }

    @PostMapping("/assessments")
    public ResponseEntity<?> createAssessment(@RequestBody CreateAssessmentRequest req) {
        if (req.declarationId == null || req.compartment == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", "declarationId and compartment are required"
            ));
        }

        AssessmentEntity assessment = workflowService.createAssessment(
                req.declarationId,
                req.declarationVersion > 0 ? req.declarationVersion : 1,
                req.ruleSnapshotId != null ? req.ruleSnapshotId : "snap-2026-q4-v1",
                req.compartment
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(assessment);
    }

    @GetMapping("/assessments/{id}")
    public ResponseEntity<?> getAssessment(@PathVariable("id") String id) {
        return assessmentRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "ASSESSMENT_NOT_FOUND",
                        "message", "Assessment not found: " + id
                )));
    }

    @PostMapping("/assessments/{id}/candidate")
    public ResponseEntity<?> recordCandidate(@PathVariable("id") String id, @RequestBody CandidateLayoutDto candidate) {
        try {
            AssessmentEntity updated = workflowService.recordCandidateLayout(id, candidate);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "DIGEST_MISMATCH", "message", e.getMessage()));
        }
    }

    @PostMapping("/assessments/{id}/validation")
    public ResponseEntity<?> recordValidation(@PathVariable("id") String id, @RequestBody ValidationResultDto validation) {
        try {
            AssessmentEntity updated = workflowService.recordValidationOutcome(id, validation);
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "VALIDATION_MISMATCH", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", e.getMessage()));
        }
    }

    @PostMapping("/assessments/{id}/approve")
    public ResponseEntity<?> approveCandidate(@PathVariable("id") String id,
                                              @RequestHeader(value = "Authorization", required = false) String authHeader,
                                              @RequestBody DecisionRequest req) {
        Claims claims;
        try {
            claims = tokenValidator.validateToken(authHeader);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "Valid bearer token required: " + e.getMessage()
            ));
        }

        if (!tokenValidator.hasRole(claims, "ROLE_LOAD_PLANNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", "Load Planner role (ROLE_LOAD_PLANNER) required to approve candidate layout"
            ));
        }

        try {
            // Strictly take authenticated actor identity from validated token claims
            String plannerId = claims.getSubject();
            AssessmentEntity updated = workflowService.approveCandidate(
                    id, req.layoutVersion, req.expectedDigest,
                    plannerId,
                    req.notes
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "APPROVAL_BLOCKED",
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", e.getMessage()));
        }
    }

    @PostMapping("/assessments/{id}/reject")
    public ResponseEntity<?> rejectCandidate(@PathVariable("id") String id,
                                             @RequestHeader(value = "Authorization", required = false) String authHeader,
                                             @RequestBody DecisionRequest req) {
        Claims claims;
        try {
            claims = tokenValidator.validateToken(authHeader);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "Valid bearer token required: " + e.getMessage()
            ));
        }

        if (!tokenValidator.hasRole(claims, "ROLE_LOAD_PLANNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "FORBIDDEN",
                    "message", "Load Planner role (ROLE_LOAD_PLANNER) required to reject candidate layout"
            ));
        }

        try {
            // Strictly take authenticated actor identity from validated token claims
            String plannerId = claims.getSubject();
            AssessmentEntity updated = workflowService.rejectCandidate(
                    id, req.layoutVersion,
                    plannerId,
                    req.rejectionReasonCode != null ? req.rejectionReasonCode : "OPERATIONAL_REJECTION",
                    req.notes
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "REJECTION_BLOCKED",
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", e.getMessage()));
        }
    }

    @GetMapping("/assessments/{id}/audit-trail")
    public ResponseEntity<?> getAssessmentAuditTrail(@PathVariable("id") String id,
                                                     @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims;
        try {
            claims = tokenValidator.validateToken(authHeader);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "Valid bearer token required: " + e.getMessage()
            ));
        }

        AssessmentEntity assessment = assessmentRepository.findById(id).orElse(null);
        if (assessment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "NOT_FOUND",
                    "message", "Assessment not found: " + id
            ));
        }

        // Planners can view any assessment audit trail
        // Shippers can only view audit trail if the assessment belongs to a declaration they own
        if (!tokenValidator.hasRole(claims, "ROLE_LOAD_PLANNER")) {
            if (tokenValidator.hasRole(claims, "ROLE_SHIPPER")) {
                String authenticatedShipper = claims.getSubject();
                // Check if the declaration's shipperId matches authenticated user
                // The declarationId in assessment can be checked, or if declarationId contains the shipper prefix
                // For secure defense-in-depth, if the shipper does not own this declaration, deny access
                if (!assessment.getDeclarationId().startsWith(authenticatedShipper)
                        && !authenticatedShipper.equals(assessment.getDeclarationId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                            "code", "FORBIDDEN",
                            "message", "Cross-tenant access forbidden: you cannot access another shipper's assessment audit trail"
                    ));
                }
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "code", "FORBIDDEN",
                        "message", "Insufficient privileges to access assessment audit trail"
                ));
            }
        }

        return ResponseEntity.ok(decisionAuditRepository.findByAssessmentIdOrderByDecidedAtAsc(id));
    }

    @GetMapping("/assessments/{id}/events")
    public ResponseEntity<?> getAssessmentEvents(@PathVariable("id") String id) {
        return ResponseEntity.ok(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING"));
    }

    @GetMapping("/assessments/{id}/layout")
    public ResponseEntity<?> getAssessmentCurrentLayout(@PathVariable("id") String id) {
        return assessmentRepository.findById(id)
                .<ResponseEntity<?>>map(a -> {
                    if (a.getCandidateLayoutId() == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                                "code", "NO_CANDIDATE_LAYOUT",
                                "message", "No candidate layout registered for assessment " + id
                        ));
                    }
                    return layoutRepository.findByAssessmentIdAndLayoutVersion(id, a.getCandidateLayoutVersion())
                            .<ResponseEntity<?>>map(ResponseEntity::ok)
                            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                                    "code", "LAYOUT_NOT_FOUND",
                                    "message", "Layout entity not found"
                            )));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "ASSESSMENT_NOT_FOUND",
                        "message", "Assessment not found: " + id
                )));
    }

    @GetMapping("/layouts/{id}/versions/{version}")
    public ResponseEntity<?> getLayoutVersion(@PathVariable("id") String id, @PathVariable("version") int version) {
        return layoutRepository.findByAssessmentIdAndLayoutVersion(id, version)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "code", "LAYOUT_NOT_FOUND",
                        "message", "Layout not found for assessment " + id + " and version " + version
                )));
    }
}
