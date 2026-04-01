package com.nowcoder.community.common.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtCodecsTest {

    @Test
    void jwtDecoder_shouldRejectTooShortSecret() {
        JwtProperties properties = properties("too-short-secret", "community-auth");

        assertThatThrownBy(() -> JwtCodecs.jwtDecoder(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.hmac-secret");
    }

    @Test
    void jwtDecoder_shouldValidateIssuer() {
        JwtProperties properties = properties("plan-test-jwt-secret-please-change-123456", "community-auth");
        JwtDecoder decoder = JwtCodecs.jwtDecoder(properties);
        String token = signHs256("plan-test-jwt-secret-please-change-123456", "wrong-issuer");

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    private static JwtProperties properties(String hmacSecret, String issuer) {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret(hmacSecret);
        properties.setIssuer(issuer);
        return properties;
    }

    private static String signHs256(String secret, String issuer) {
        try {
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject("123")
                    .issuer(issuer)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .build();
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
