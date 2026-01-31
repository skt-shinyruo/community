package com.nowcoder.community.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Value("${security.jwt.hmac-secret}")
    private String hmacSecret;

    @Test
    void internalPathsShouldBeExplicitlyDenied() {
        webTestClient.get()
                .uri("/internal/anything")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_ADMIN")))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void opsPathsShouldRequireAdminRole() {
        webTestClient.get()
                .uri("/api/ops/ping")
                .exchange()
                .expectStatus()
                .isUnauthorized();

        webTestClient.get()
                .uri("/api/ops/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_USER")))
                .exchange()
                .expectStatus()
                .isForbidden();

        webTestClient.get()
                .uri("/api/ops/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_ADMIN")))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void legacySearchInternalPathsShouldRequireAdminRole() {
        webTestClient.get()
                .uri("/api/search/internal/ping")
                .exchange()
                .expectStatus()
                .isUnauthorized();

        webTestClient.get()
                .uri("/api/search/internal/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_USER")))
                .exchange()
                .expectStatus()
                .isForbidden();

        webTestClient.get()
                .uri("/api/search/internal/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_ADMIN")))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    private String tokenWithAuthorities(List<String> authorities) {
        try {
            byte[] secretBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
            JWSSigner signer = new MACSigner(secretBytes);

            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("1")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .claim("authorities", authorities)
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to build test JWT", e);
        }
    }
}
