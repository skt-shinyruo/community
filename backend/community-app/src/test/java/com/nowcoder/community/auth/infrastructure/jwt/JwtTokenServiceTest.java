package com.nowcoder.community.auth.infrastructure.jwt;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void createAccessTokenShouldIncludeSecurityVersionClaim() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setIssuer("community-auth-test");
        properties.setAccessTokenTtlSeconds(900);
        JwtEncoder encoder = JwtCodecs.jwtEncoder(properties);
        JwtDecoder decoder = JwtCodecs.jwtDecoder(properties);
        JwtTokenService service = new JwtTokenService(encoder, properties);

        String token = service.createAccessToken(
                UUID.fromString("00000000-0000-7000-8000-000000000007"),
                "alice",
                List.of("ROLE_USER"),
                123L
        );

        Long securityVersion = decoder.decode(token).getClaim("security_version");
        assertThat(securityVersion).isEqualTo(123L);
    }
}
