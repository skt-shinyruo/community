package com.nowcoder.community.auth.infrastructure.jwt;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final KeyPair ACCESS_KEY_PAIR = rsaKeyPair();

    @Test
    void createAccessTokenShouldIncludeSecurityVersionClaim() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessPublicKey(Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPublic().getEncoded()));
        properties.setAccessPrivateKey(Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPrivate().getEncoded()));
        properties.setIssuer("community-auth-test");
        properties.setAccessTokenAudience("community-api-test");
        properties.setAccessTokenTtlSeconds(900);
        JwtEncoder encoder = JwtCodecs.accessTokenEncoder(properties);
        JwtDecoder decoder = JwtCodecs.accessTokenDecoder(properties);
        JwtTokenService service = new JwtTokenService(encoder, properties, Clock.systemUTC());

        String token = service.createAccessToken(
                UUID.fromString("00000000-0000-7000-8000-000000000007"),
                "alice",
                List.of("ROLE_USER"),
                123L
        );

        var decoded = decoder.decode(token);
        Long securityVersion = decoded.getClaim("security_version");
        assertThat(securityVersion).isEqualTo(123L);
        assertThat(decoded.getAudience()).containsExactly("community-api-test");
        assertThat(decoded.getHeaders())
                .containsEntry("alg", "RS256")
                .containsEntry("typ", JwtCodecs.ACCESS_TOKEN_TYPE);
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
