package com.nowcoder.community.common.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void jwtEncoder_shouldRejectBlankIssuer() {
        JwtProperties properties = properties("plan-test-jwt-secret-please-change-123456", "   ");

        assertThatThrownBy(() -> JwtCodecs.jwtEncoder(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.issuer");
    }

    @Test
    void jwtEncoder_shouldRejectMissingIssuer() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret("plan-test-jwt-secret-please-change-123456");

        assertThatThrownBy(() -> JwtCodecs.jwtEncoder(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.issuer");
    }

    @Test
    void jwtEncoderAndDecoder_shouldBeInteroperable() {
        JwtProperties properties = properties("plan-test-jwt-secret-please-change-123456", "community-auth");
        JwtEncoder encoder = JwtCodecs.jwtEncoder(properties);
        JwtDecoder decoder = JwtCodecs.jwtDecoder(properties);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("123")
                .issuer(JwtCodecs.resolvedIssuer(properties))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        Jwt decoded = decoder.decode(token);
        assertThat(decoded.getSubject()).isEqualTo("123");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("community-auth");
    }

    @Test
    void resolvedIssuer_shouldRejectBlankValue() {
        JwtProperties properties = properties("plan-test-jwt-secret-please-change-123456", "   ");

        assertThatThrownBy(() -> JwtCodecs.resolvedIssuer(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.issuer");
    }

    @Test
    void resolvedIssuer_shouldRejectMissingValue() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret("plan-test-jwt-secret-please-change-123456");

        assertThatThrownBy(() -> JwtCodecs.resolvedIssuer(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.issuer");
    }

    @Test
    void resolvedIssuer_shouldTrimValue() {
        JwtProperties properties = properties("plan-test-jwt-secret-please-change-123456", "  issuer-a  ");

        assertThat(JwtCodecs.resolvedIssuer(properties)).isEqualTo("issuer-a");
    }

    @Test
    void hmacSha256OrThrow_shouldRejectKnownPlaceholderSecrets() {
        JwtProperties firstPlaceholder = properties("dev-secret-please-change-at-least-32bytes", "community-auth");
        JwtProperties secondPlaceholder = properties("dev-jwt-hmac-secret-please-change-me-123456", "community-auth");

        assertThatThrownBy(() -> JwtSecretKeys.hmacSha256OrThrow(firstPlaceholder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.hmac-secret");
        assertThatThrownBy(() -> JwtSecretKeys.hmacSha256OrThrow(secondPlaceholder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.hmac-secret");
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
