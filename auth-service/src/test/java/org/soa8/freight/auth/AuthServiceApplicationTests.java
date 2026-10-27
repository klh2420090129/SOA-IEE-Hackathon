package org.soa8.freight.auth;

import org.junit.jupiter.api.Test;
import org.soa8.freight.auth.security.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "eureka.client.enabled=false"
})
class AuthServiceApplicationTests {

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void shouldGenerateAndValidateJwtToken() {
        String token = jwtProvider.generateToken("test-uid", "testuser", Set.of("ROLE_SHIPPER"));
        assertThat(token).isNotBlank();

        var claims = jwtProvider.validateAndGetClaims(token);
        assertThat(claims.getSubject()).isEqualTo("test-uid");
        assertThat(claims.get("username", String.class)).isEqualTo("testuser");
    }
}
