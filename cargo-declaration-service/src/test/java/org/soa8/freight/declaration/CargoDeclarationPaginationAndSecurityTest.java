package org.soa8.freight.declaration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soa8.freight.declaration.controller.CargoDeclarationController;
import org.soa8.freight.declaration.model.CargoDeclaration;
import org.soa8.freight.declaration.model.CargoItemEntity;
import org.soa8.freight.declaration.repository.CargoDeclarationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false"
})
class CargoDeclarationPaginationAndSecurityTest {

    @Autowired
    private CargoDeclarationController declarationController;

    @Autowired
    private CargoDeclarationRepository declarationRepository;

    @Value("${jwt.secret:soa-team8-default-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256!}")
    private String jwtSecret;

    @Value("${jwt.issuer:soa-team8-auth-service}")
    private String jwtIssuer;

    @Value("${jwt.audience:soa-team8-platform}")
    private String jwtAudience;

    private SecretKey key;

    @BeforeEach
    void setUp() {
        key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        declarationRepository.deleteAll();
    }

    private String generateTestToken(String userId, String username, List<String> roles) {
        long now = System.currentTimeMillis();
        return "Bearer " + Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("username", username)
                .claim("roles", roles)
                .issuer(jwtIssuer)
                .audience().add(jwtAudience).and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3600000))
                .signWith(key)
                .compact();
    }

    private CargoDeclaration createDeclaration(String id, String ref, String shipperId, String status) {
        CargoDeclaration decl = new CargoDeclaration(id, ref, shipperId, "Origin", "Destination");
        decl.setStatus(status);
        CargoItemEntity item = new CargoItemEntity(
                UUID.randomUUID().toString(), "Item", "3", "UN1203", 1000, 1000, 1000, 500.0, true, 1000.0, 1
        );
        decl.setItems(List.of(item));
        return declarationRepository.save(decl);
    }

    @Test
    void shouldRejectListingWithMissingOrInvalidToken() {
        // Missing token -> 401
        ResponseEntity<?> noTokenResp = declarationController.listDeclarations(null, 0, 10, null, null);
        assertThat(noTokenResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Invalid token -> 401
        ResponseEntity<?> badTokenResp = declarationController.listDeclarations("Bearer invalid-token", 0, 10, null, null);
        assertThat(badTokenResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Unauthorized role (neither shipper nor planner) -> 403
        String guestToken = generateTestToken("guest-01", "guest", List.of("ROLE_VIEWER"));
        ResponseEntity<?> guestResp = declarationController.listDeclarations(guestToken, 0, 10, null, null);
        assertThat(guestResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldEnforceShipperTenantIsolation() {
        // Seed declarations for two different shippers
        createDeclaration("decl-s1-1", "REF-S1-1", "shipper-alpha", "SUBMITTED");
        createDeclaration("decl-s1-2", "REF-S1-2", "shipper-alpha", "APPROVED");
        createDeclaration("decl-s2-1", "REF-S2-1", "shipper-beta", "SUBMITTED");

        String shipperAlphaToken = generateTestToken("shipper-alpha", "shipper-alpha", List.of("ROLE_SHIPPER"));

        // Shipper Alpha lists declarations -> should ONLY see declarations for shipper-alpha
        ResponseEntity<?> resp = declarationController.listDeclarations(shipperAlphaToken, 0, 10, null, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertThat(body.get("totalElements")).isEqualTo(2L);
        List<?> items = (List<?>) body.get("content");
        assertThat(items).hasSize(2);
        for (Object item : items) {
            CargoDeclaration decl = (CargoDeclaration) item;
            assertThat(decl.getShipperId()).isEqualTo("shipper-alpha");
        }
    }

    @Test
    void shouldAllowLoadPlannerGlobalVisibilityAcrossTenants() {
        createDeclaration("decl-p-1", "REF-1", "shipper-alpha", "SUBMITTED");
        createDeclaration("decl-p-2", "REF-2", "shipper-beta", "APPROVED");
        createDeclaration("decl-p-3", "REF-3", "shipper-gamma", "REJECTED");

        String plannerToken = generateTestToken("planner-01", "planner-01", List.of("ROLE_LOAD_PLANNER"));

        // Load planner sees all declarations across tenants
        ResponseEntity<?> resp = declarationController.listDeclarations(plannerToken, 0, 10, null, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertThat(body.get("totalElements")).isEqualTo(3L);
        List<?> items = (List<?>) body.get("content");
        assertThat(items).hasSize(3);

        // Load planner filtering by specific shipper
        ResponseEntity<?> filteredResp = declarationController.listDeclarations(plannerToken, 0, 10, null, "shipper-beta");
        Map<?, ?> filteredBody = (Map<?, ?>) filteredResp.getBody();
        assertThat(filteredBody.get("totalElements")).isEqualTo(1L);
        CargoDeclaration single = (CargoDeclaration) ((List<?>) filteredBody.get("content")).get(0);
        assertThat(single.getShipperId()).isEqualTo("shipper-beta");
    }

    @Test
    void shouldFilterByStatusAndPaginateDeterministically() {
        String shipperToken = generateTestToken("shipper-delta", "shipper-delta", List.of("ROLE_SHIPPER"));

        for (int i = 1; i <= 5; i++) {
            createDeclaration("decl-d-" + i, "REF-" + i, "shipper-delta", i % 2 == 0 ? "APPROVED" : "SUBMITTED");
        }

        // Filter by SUBMITTED (3 items total: 1, 3, 5)
        ResponseEntity<?> statusResp = declarationController.listDeclarations(shipperToken, 0, 2, "SUBMITTED", null);
        Map<?, ?> statusBody = (Map<?, ?>) statusResp.getBody();
        assertThat(statusBody.get("totalElements")).isEqualTo(3L);
        assertThat(statusBody.get("totalPages")).isEqualTo(2);
        List<?> page0Items = (List<?>) statusBody.get("content");
        assertThat(page0Items).hasSize(2);

        // Query page 1
        ResponseEntity<?> page1Resp = declarationController.listDeclarations(shipperToken, 1, 2, "SUBMITTED", null);
        Map<?, ?> page1Body = (Map<?, ?>) page1Resp.getBody();
        List<?> page1Items = (List<?>) page1Body.get("content");
        assertThat(page1Items).hasSize(1);
    }

    @Test
    void shouldNormalizeInvalidPaginationParametersSafely() {
        String shipperToken = generateTestToken("shipper-norm", "shipper-norm", List.of("ROLE_SHIPPER"));
        createDeclaration("decl-n-1", "REF-N1", "shipper-norm", "SUBMITTED");

        // Negative page (-5) and huge size (500) -> safe normalization to page 0, size 100
        ResponseEntity<?> resp = declarationController.listDeclarations(shipperToken, -5, 500, null, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertThat(body.get("page")).isEqualTo(0);
        assertThat(body.get("size")).isEqualTo(100);
        assertThat(body.get("totalElements")).isEqualTo(1L);
    }

    @Test
    void shouldEnforceSecurityOnGetDeclarationById() {
        createDeclaration("decl-sec-1", "REF-SEC-1", "shipper-alpha", "SUBMITTED");
        String shipperAlphaToken = generateTestToken("shipper-alpha", "shipper-alpha", List.of("ROLE_SHIPPER"));
        String shipperBetaToken = generateTestToken("shipper-beta", "shipper-beta", List.of("ROLE_SHIPPER"));
        String plannerToken = generateTestToken("planner-01", "planner-01", List.of("ROLE_LOAD_PLANNER"));
        String guestToken = generateTestToken("guest-01", "guest", List.of("ROLE_VIEWER"));

        // 1. Missing token -> 401
        ResponseEntity<?> noTokenResp = declarationController.getDeclaration(null, "decl-sec-1");
        assertThat(noTokenResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. Invalid token -> 401
        ResponseEntity<?> badTokenResp = declarationController.getDeclaration("Bearer invalid-jwt", "decl-sec-1");
        assertThat(badTokenResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3. Unauthorized role -> 403
        ResponseEntity<?> guestResp = declarationController.getDeclaration(guestToken, "decl-sec-1");
        assertThat(guestResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. Non-existent declaration with valid token -> 404
        ResponseEntity<?> notFoundResp = declarationController.getDeclaration(shipperAlphaToken, "non-existent-id");
        assertThat(notFoundResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 5. Shipper reading own declaration -> 200 OK
        ResponseEntity<?> ownResp = declarationController.getDeclaration(shipperAlphaToken, "decl-sec-1");
        assertThat(ownResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        CargoDeclaration ownDecl = (CargoDeclaration) ownResp.getBody();
        assertThat(ownDecl.getId()).isEqualTo("decl-sec-1");

        // 6. Shipper attempting cross-tenant read of another shipper's declaration -> 403 FORBIDDEN
        ResponseEntity<?> crossTenantResp = declarationController.getDeclaration(shipperBetaToken, "decl-sec-1");
        assertThat(crossTenantResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 7. Load planner reading any declaration -> 200 OK
        ResponseEntity<?> plannerResp = declarationController.getDeclaration(plannerToken, "decl-sec-1");
        assertThat(plannerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldEnforceSecurityOnGetWorkflowProjection() {
        createDeclaration("decl-proj-1", "REF-PROJ-1", "shipper-alpha", "SUBMITTED");
        String shipperAlphaToken = generateTestToken("shipper-alpha", "shipper-alpha", List.of("ROLE_SHIPPER"));
        String shipperBetaToken = generateTestToken("shipper-beta", "shipper-beta", List.of("ROLE_SHIPPER"));
        String plannerToken = generateTestToken("planner-01", "planner-01", List.of("ROLE_LOAD_PLANNER"));
        String guestToken = generateTestToken("guest-01", "guest", List.of("ROLE_VIEWER"));

        // 1. Missing token -> 401
        ResponseEntity<?> noTokenResp = declarationController.getWorkflowProjection(null, "decl-proj-1");
        assertThat(noTokenResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. Invalid token -> 401
        ResponseEntity<?> badTokenResp = declarationController.getWorkflowProjection("Bearer invalid-jwt", "decl-proj-1");
        assertThat(badTokenResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3. Unauthorized role -> 403
        ResponseEntity<?> guestResp = declarationController.getWorkflowProjection(guestToken, "decl-proj-1");
        assertThat(guestResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. Non-existent declaration -> 404
        ResponseEntity<?> notFoundResp = declarationController.getWorkflowProjection(shipperAlphaToken, "non-existent-id");
        assertThat(notFoundResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 5. Shipper reading own declaration workflow projection -> 200 OK
        ResponseEntity<?> ownResp = declarationController.getWorkflowProjection(shipperAlphaToken, "decl-proj-1");
        assertThat(ownResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 6. Shipper attempting cross-tenant read of another shipper's workflow projection -> 403 FORBIDDEN
        ResponseEntity<?> crossTenantResp = declarationController.getWorkflowProjection(shipperBetaToken, "decl-proj-1");
        assertThat(crossTenantResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 7. Load planner reading any workflow projection -> 200 OK
        ResponseEntity<?> plannerResp = declarationController.getWorkflowProjection(plannerToken, "decl-proj-1");
        assertThat(plannerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
