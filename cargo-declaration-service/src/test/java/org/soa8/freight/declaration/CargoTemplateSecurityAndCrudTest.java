package org.soa8.freight.declaration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soa8.freight.declaration.controller.CargoTemplateController;
import org.soa8.freight.declaration.model.CargoTemplate;
import org.soa8.freight.declaration.repository.CargoTemplateRepository;
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
class CargoTemplateSecurityAndCrudTest {

    @Autowired
    private CargoTemplateController templateController;

    @Autowired
    private CargoTemplateRepository templateRepository;

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
        templateRepository.deleteAll();
    }

    private String generateTestToken(String userId, String username, List<String> roles, long expirationOffsetMs) {
        long now = System.currentTimeMillis();
        return "Bearer " + Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("username", username)
                .claim("roles", roles)
                .issuer(jwtIssuer)
                .audience().add(jwtAudience).and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationOffsetMs))
                .signWith(key)
                .compact();
    }

    private CargoTemplateController.TemplateItemRequest buildValidItem(String id) {
        CargoTemplateController.TemplateItemRequest item = new CargoTemplateController.TemplateItemRequest();
        item.cargoItemId = id;
        item.description = "Flammable Liquid Drum";
        item.hazardClass = "3";
        item.unNumber = "UN1993";
        item.lengthMm = 1200;
        item.widthMm = 1000;
        item.heightMm = 1000;
        item.weightKg = 500.0;
        item.stackable = true;
        item.maxStackWeightKg = 1000.0;
        item.quantity = 2;
        return item;
    }

    @Test
    void shouldRejectWhenMissingOrInvalidAuthHeader() {
        CargoTemplateController.SaveTemplateRequest req = new CargoTemplateController.SaveTemplateRequest();
        req.name = "My Template";
        req.items = List.of(buildValidItem("ITEM-01"));

        // Null token
        ResponseEntity<?> res1 = templateController.createTemplate(null, req);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Malformed token prefix
        ResponseEntity<?> res2 = templateController.createTemplate("Basic xyz", req);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenTokenIsExpired() {
        String expiredToken = generateTestToken("shipper-1", "shipper", List.of("ROLE_SHIPPER"), -10000);
        CargoTemplateController.SaveTemplateRequest req = new CargoTemplateController.SaveTemplateRequest();
        req.name = "My Template";
        req.items = List.of(buildValidItem("ITEM-01"));

        ResponseEntity<?> res = templateController.createTemplate(expiredToken, req);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenUserDoesNotHaveRoleShipper() {
        // Planner token trying to access shipper template API
        String plannerToken = generateTestToken("planner-1", "planner", List.of("ROLE_PLANNER"), 3600000);
        CargoTemplateController.SaveTemplateRequest req = new CargoTemplateController.SaveTemplateRequest();
        req.name = "My Template";
        req.items = List.of(buildValidItem("ITEM-01"));

        ResponseEntity<?> res = templateController.createTemplate(plannerToken, req);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldValidateCargoItemInputsStrictly() {
        String shipperToken = generateTestToken("shipper-1", "shipper", List.of("ROLE_SHIPPER"), 3600000);

        // Test 1: Empty name
        CargoTemplateController.SaveTemplateRequest reqEmptyName = new CargoTemplateController.SaveTemplateRequest();
        reqEmptyName.name = "   ";
        reqEmptyName.items = List.of(buildValidItem("ITEM-01"));
        ResponseEntity<?> res1 = templateController.createTemplate(shipperToken, reqEmptyName);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Test 2: Empty items list
        CargoTemplateController.SaveTemplateRequest reqEmptyItems = new CargoTemplateController.SaveTemplateRequest();
        reqEmptyItems.name = "Empty Items Template";
        reqEmptyItems.items = List.of();
        ResponseEntity<?> res2 = templateController.createTemplate(shipperToken, reqEmptyItems);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Test 3: Invalid hazard class & malformed UN number
        CargoTemplateController.TemplateItemRequest badItem = buildValidItem("ITEM-BAD");
        badItem.hazardClass = "NON_HAZARDOUS";
        badItem.unNumber = "1993"; // Missing 'UN' prefix
        badItem.lengthMm = -50;
        CargoTemplateController.SaveTemplateRequest reqBadItem = new CargoTemplateController.SaveTemplateRequest();
        reqBadItem.name = "Bad Item Template";
        reqBadItem.items = List.of(badItem);
        ResponseEntity<?> res3 = templateController.createTemplate(shipperToken, reqBadItem);
        assertThat(res3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) res3.getBody();
        assertThat(body.get("errors").toString()).contains("unrecognized statutory hazard class");
        assertThat(body.get("errors").toString()).contains("invalid UN number format");
        assertThat(body.get("errors").toString()).contains("lengthMm must be a positive integer");
    }

    @Test
    void shouldCreateListUpdateAndDeleteTemplateSuccessfully() {
        String shipperToken = generateTestToken("shipper-1", "shipper", List.of("ROLE_SHIPPER"), 3600000);

        // 1. Create Template
        CargoTemplateController.SaveTemplateRequest createReq = new CargoTemplateController.SaveTemplateRequest();
        createReq.name = "Solvent Master Template";
        createReq.description = "2 drums of solvent";
        createReq.items = List.of(buildValidItem("ITEM-01"), buildValidItem("ITEM-02"));

        ResponseEntity<?> createRes = templateController.createTemplate(shipperToken, createReq);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CargoTemplate created = (CargoTemplate) createRes.getBody();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getShipperId()).isEqualTo("shipper-1");
        assertThat(created.getItems()).hasSize(2);

        String templateId = created.getId();

        // 2. List Templates
        ResponseEntity<?> listRes = templateController.listTemplates(shipperToken);
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> list = (List<?>) listRes.getBody();
        assertThat(list).hasSize(1);

        // 3. Get Template
        ResponseEntity<?> getRes = templateController.getTemplate(shipperToken, templateId);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        CargoTemplate fetched = (CargoTemplate) getRes.getBody();
        assertThat(fetched.getName()).isEqualTo("Solvent Master Template");

        // 4. Update Template (rename)
        CargoTemplateController.UpdateTemplateRequest updateReq = new CargoTemplateController.UpdateTemplateRequest();
        updateReq.name = "Renamed Solvent Template";
        ResponseEntity<?> updateRes = templateController.updateTemplate(shipperToken, templateId, updateReq);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        CargoTemplate updated = (CargoTemplate) updateRes.getBody();
        assertThat(updated.getName()).isEqualTo("Renamed Solvent Template");

        // 5. Delete Template
        ResponseEntity<?> deleteRes = templateController.deleteTemplate(shipperToken, templateId);
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify gone
        ResponseEntity<?> getAfterDelete = templateController.getTemplate(shipperToken, templateId);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldEnforceStrictCrossShipperIsolation() {
        String shipper1Token = generateTestToken("shipper-1", "shipper-one", List.of("ROLE_SHIPPER"), 3600000);
        String shipper2Token = generateTestToken("shipper-2", "shipper-two", List.of("ROLE_SHIPPER"), 3600000);

        // Shipper 1 creates a template
        CargoTemplateController.SaveTemplateRequest req = new CargoTemplateController.SaveTemplateRequest();
        req.name = "Shipper 1 Private Template";
        req.items = List.of(buildValidItem("S1-ITEM-01"));
        ResponseEntity<?> createRes = templateController.createTemplate(shipper1Token, req);
        CargoTemplate created = (CargoTemplate) createRes.getBody();
        String templateId = created.getId();

        // Shipper 2 lists templates -> Should see 0 templates
        ResponseEntity<?> s2ListRes = templateController.listTemplates(shipper2Token);
        assertThat(s2ListRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> s2List = (List<?>) s2ListRes.getBody();
        assertThat(s2List).isEmpty();

        // Shipper 2 attempts to get Shipper 1's template by ID -> 404 NOT_FOUND
        ResponseEntity<?> s2GetRes = templateController.getTemplate(shipper2Token, templateId);
        assertThat(s2GetRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Shipper 2 attempts to update Shipper 1's template -> 404 NOT_FOUND
        CargoTemplateController.UpdateTemplateRequest updateReq = new CargoTemplateController.UpdateTemplateRequest();
        updateReq.name = "Hacked Template Name";
        ResponseEntity<?> s2UpdateRes = templateController.updateTemplate(shipper2Token, templateId, updateReq);
        assertThat(s2UpdateRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Shipper 2 attempts to delete Shipper 1's template -> 404 NOT_FOUND
        ResponseEntity<?> s2DeleteRes = templateController.deleteTemplate(shipper2Token, templateId);
        assertThat(s2DeleteRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify Shipper 1's template is completely intact
        ResponseEntity<?> s1GetRes = templateController.getTemplate(shipper1Token, templateId);
        assertThat(s1GetRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        CargoTemplate intact = (CargoTemplate) s1GetRes.getBody();
        assertThat(intact.getName()).isEqualTo("Shipper 1 Private Template");
    }
}
