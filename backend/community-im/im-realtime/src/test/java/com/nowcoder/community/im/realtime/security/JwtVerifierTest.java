package com.nowcoder.community.im.realtime.security;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierTest {

    @Test
    void verify_shouldExtractUserIdFromSharedJwtSubjectParser() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret("im-realtime-test-jwt-hmac-secret-please-change-123456");
        properties.setIssuer("community-auth");
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000123");
        String token = signHs256(properties.getHmacSecret(), properties.getIssuer(), userId.toString(), Instant.now().plusSeconds(60));

        JwtDecoder decoder = JwtCodecs.jwtDecoder(properties);
        JwtVerifier verifier = new JwtVerifier(decoder);

        JwtVerifier.VerifiedJwt verified = verifier.verify(token);
        assertThat(verified.userId()).isEqualTo(userId);
        assertThat(verified.jwt().getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void verify_shouldFailForInvalidSignature() throws Exception {
        JwtProperties signingProperties = new JwtProperties();
        signingProperties.setHmacSecret("im-realtime-test-jwt-hmac-secret-please-change-123456");
        signingProperties.setIssuer("community-auth");
        String token = signHs256(
                signingProperties.getHmacSecret(),
                signingProperties.getIssuer(),
                "00000000-0000-7000-8000-000000000001",
                Instant.now().plusSeconds(60)
        );

        JwtProperties decoderProperties = new JwtProperties();
        decoderProperties.setHmacSecret("im-realtime-alt-jwt-hmac-secret-please-change-123456");
        decoderProperties.setIssuer("community-auth");
        JwtDecoder decoder = JwtCodecs.jwtDecoder(decoderProperties);
        JwtVerifier verifier = new JwtVerifier(decoder);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(Exception.class);
    }

    private static String signHs256(String secret, String issuer, String sub, Instant exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(sub)
                .issueTime(new Date())
                .expirationTime(Date.from(exp))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
