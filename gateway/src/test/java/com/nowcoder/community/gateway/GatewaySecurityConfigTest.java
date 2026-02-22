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

    @Value("${community.metrics.basic-auth.password}")
    private String metricsBasicAuthPassword;

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
    void transparentModeShouldNotBlockPublicTrafficAtGateway() {
        // 透明模式下 gateway 不做业务授权矩阵。这里使用“无实例服务”的 503 作为信号：
        // - 若 gateway 做了认证拦截，会先返回 401/403
        // - 若 gateway 透明转发，则路由匹配后会因无法发现实例返回 503
        webTestClient.get()
                .uri("/api/posts")
                .exchange()
                .expectStatus()
                .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void prometheusEndpointShouldRequireBasicAuth() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isUnauthorized();

        webTestClient.get()
                .uri("/actuator/prometheus")
                .headers(h -> h.setBasicAuth("prometheus", metricsBasicAuthPassword))
                .exchange()
                // 若未启用 prometheus endpoint，最终会是 404；但不应被 401/403 遮蔽（安全链路应允许通过）。
                .expectStatus()
                .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403));
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
