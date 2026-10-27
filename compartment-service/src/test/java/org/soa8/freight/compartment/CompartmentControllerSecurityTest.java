package org.soa8.freight.compartment;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.soa8.freight.common.model.CandidateLayoutDto;
import org.soa8.freight.common.model.CompartmentDto;
import org.soa8.freight.common.model.ValidationResultDto;
import org.soa8.freight.compartment.controller.CompartmentController;
import org.soa8.freight.compartment.model.AssessmentEntity;
import org.soa8.freight.compartment.service.AssessmentWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false"
})
class CompartmentControllerSecurityTest {

    @Autowired
    private CompartmentController controller;

    @Autowired
    private AssessmentWorkflowService workflowService;

    @Value("${jwt.secret:soa-team8-default-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256!}")
    private String jwtSecret;

    @Value("${jwt.issuer:soa-team8-auth-service}")
    private String issuer;

    @Value("${jwt.audience:soa-team8-platform}")
    private String audience;

    private String generateToken(String sub, Set<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(sub)
                .claim("username", sub)
                .claim("roles", List.copyOf(roles))
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Approve and Reject endpoints enforce ROLE_LOAD_PLANNER on backend")
    void shouldEnforcePlannerRoleOnDecisions() {
        CompartmentDto comp = new CompartmentDto("comp-sec-1", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-sec-001", 1, "snap-001", comp);
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-sec-1", 1, "comp-sec-1", null, "FEASIBLE", "CONTROLLED_STUB", Collections.emptyList()
        );
        assessment = workflowService.recordCandidateLayout(assessment.getId(), candidate);
        ValidationResultDto valPassed = new ValidationResultDto("val-sec-1", true, "1.0.0", 1, Collections.emptyList());
        assessment = workflowService.recordValidationOutcome(assessment.getId(), valPassed);

        CompartmentController.DecisionRequest req = new CompartmentController.DecisionRequest();
        req.layoutVersion = 1;
        req.expectedDigest = assessment.getCandidateContentDigest();
        req.plannerId = "planner-001";
        req.notes = "Approval test";

        // Case 1: Missing token -> 401 Unauthorized
        ResponseEntity<?> noTokenResp = controller.approveCandidate(assessment.getId(), null, req);
        assertThat(noTokenResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Case 2: Shipper token (ROLE_SHIPPER only) -> 403 Forbidden
        String shipperToken = "Bearer " + generateToken("shipper-001", Set.of("ROLE_SHIPPER"));
        ResponseEntity<?> shipperApproveResp = controller.approveCandidate(assessment.getId(), shipperToken, req);
        assertThat(shipperApproveResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Map<?, ?> errMap = (Map<?, ?>) shipperApproveResp.getBody();
        assertThat(errMap.get("code")).isEqualTo("FORBIDDEN");

        // Case 3: Shipper token attempting reject -> 403 Forbidden
        ResponseEntity<?> shipperRejectResp = controller.rejectCandidate(assessment.getId(), shipperToken, req);
        assertThat(shipperRejectResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Case 4: Planner token (ROLE_LOAD_PLANNER) -> 200 OK
        String plannerToken = "Bearer " + generateToken("planner-001", Set.of("ROLE_LOAD_PLANNER"));
        ResponseEntity<?> plannerApproveResp = controller.approveCandidate(assessment.getId(), plannerToken, req);
        assertThat(plannerApproveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AssessmentEntity approved = (AssessmentEntity) plannerApproveResp.getBody();
        assertThat(approved.getWorkflowStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Candidate with VALIDATION_FAILED cannot be approved even by authorized Planner")
    void shouldBlockApprovalWhenValidationFailed() {
        CompartmentDto comp = new CompartmentDto("comp-sec-2", "Standard Trailer", 12000, 2400, 2600, 25000);
        AssessmentEntity assessment = workflowService.createAssessment("decl-sec-002", 1, "snap-001", comp);
        CandidateLayoutDto candidate = new CandidateLayoutDto(
                "layout-sec-2", 1, "comp-sec-2", null, "FEASIBLE", "CONTROLLED_STUB", Collections.emptyList()
        );
        assessment = workflowService.recordCandidateLayout(assessment.getId(), candidate);
        
        // Record validation failed
        ValidationResultDto valFailed = new ValidationResultDto("val-failed-1", false, "1.0.0", 1, Collections.emptyList());
        assessment = workflowService.recordValidationOutcome(assessment.getId(), valFailed);
        assertThat(assessment.getWorkflowStatus()).isEqualTo("VALIDATION_FAILED");

        CompartmentController.DecisionRequest req = new CompartmentController.DecisionRequest();
        req.layoutVersion = 1;
        req.expectedDigest = assessment.getCandidateContentDigest();
        req.plannerId = "planner-001";
        req.notes = "Attempt approval on failed candidate";

        String plannerToken = "Bearer " + generateToken("planner-001", Set.of("ROLE_LOAD_PLANNER"));
        ResponseEntity<?> approveResp = controller.approveCandidate(assessment.getId(), plannerToken, req);
        assertThat(approveResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<?, ?> errMap = (Map<?, ?>) approveResp.getBody();
        assertThat(errMap.get("code")).isEqualTo("APPROVAL_BLOCKED");
    }
}
