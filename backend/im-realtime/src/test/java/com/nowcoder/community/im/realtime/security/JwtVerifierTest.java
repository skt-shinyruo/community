package com.nowcoder.community.im.realtime.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierTest {

    @Test
    void verify_shouldExtractUserIdFromSub() throws Exception {
        String secret = "dev-secret-please-change-at-least-32bytes";
        String token = signHs256(secret, "123", Instant.now().plusSeconds(60));

        SecretKey key = JwtSecretSupport.hmacSha256KeyOrThrow(secret);
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtVerifier verifier = new JwtVerifier(decoder);

        JwtVerifier.VerifiedJwt verified = verifier.verify(token);
        assertThat(verified.userId()).isEqualTo(123);
        assertThat(verified.jwt().getSubject()).isEqualTo("123");
    }

    @Test
    void verify_shouldFailForInvalidSignature() throws Exception {
        String secret = "dev-secret-please-change-at-least-32bytes";
        String token = signHs256(secret, "1", Instant.now().plusSeconds(60));

        SecretKey key = JwtSecretSupport.hmacSha256KeyOrThrow("another-secret-please-change-at-least-32bytes");
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtVerifier verifier = new JwtVerifier(decoder);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(Exception.class);
    }

    private static String signHs256(String secret, String sub, Instant exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .issueTime(new Date())
                .expirationTime(Date.from(exp))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}

