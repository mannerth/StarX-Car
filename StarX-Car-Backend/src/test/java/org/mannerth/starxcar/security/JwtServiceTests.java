package org.mannerth.starxcar.security;

import org.mannerth.starxcar.config.JwtProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;




class JwtServiceTests {

    @Test
    void createsAndParsesTokenWithClaims() {
        JwtService jwtService = jwtService(3600);

        String token = jwtService.createToken("user-42", Map.of("role", "DRIVER"));

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.parseToken(token))
                .hasValueSatisfying(claims -> {
                    assertThat(claims.getSubject()).isEqualTo("user-42");
                    assertThat(claims.get("role", String.class)).isEqualTo("DRIVER");
                });
    }

    @Test
    void rejectsModifiedToken() {
        JwtService jwtService = jwtService(3600);
        String token = jwtService.createToken("user-42");

        assertThat(jwtService.isValid(token + "invalid")).isFalse();
    }

    private JwtService jwtService(long expire) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-used-to-create-a-stable-hmac-signing-key");
        properties.setExpire(expire);
        return new JwtService(properties);
    }
}