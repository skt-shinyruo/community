package com.nowcoder.community.im.realtime.security;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.common.security.autoconfig.SecurityCommonAutoConfiguration;
import com.nowcoder.community.im.common.ImContractVersions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = {
        "im.session.worker-id=worker-a",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.config.import="
})
class ImRealtimeSecurityIntegrationTest {

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private JwtProperties jwtProperties;

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(256 * 1024))
                        .build())
                .build();
    }

    @Test
    void unknownEndpoint_shouldReturnUnifiedResultBody() {
        client()
                .get()
                .uri("/__should_be_denied__")
                .exchange()
                .expectStatus().value(code -> assertThat(code).isIn(401, 403))
                .expectBody()
                .jsonPath("$.code").exists()
                .jsonPath("$.traceId").exists();
    }

    @Test
    void jwtDecoder_shouldComeFromSharedAutoConfiguration() {
        assertThat(applicationContext.getBeanFactory().getBeanDefinition("jwtDecoder").getFactoryBeanName())
                .isEqualTo(SecurityCommonAutoConfiguration.class.getName());
    }

    @Test
    void actuatorHealth_shouldBePublic() {
        client()
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void internalFanoutEndpoint_requiresRealtimeInternalScope() {
        String body = """
                {
                  "schemaVersion": %d,
                  "targetWorkerId": "worker-a",
                  "roomId": "00000000-0000-7000-8000-000000000001",
                  "lastSeq": 42,
                  "sourceEventId": "evt-1",
                  "createdAtEpochMs": 1000
                }
                """.formatted(ImContractVersions.KAFKA_COMMAND_SCHEMA_VERSION);

        client()
                .post()
                .uri("/internal/im/realtime/fanout/room")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().value(code -> assertThat(code).isIn(401, 403));

        client()
                .post()
                .uri("/internal/im/realtime/fanout/room")
                .header("Authorization", bearer("profile.read"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isForbidden();

        client()
                .post()
                .uri("/internal/im/realtime/fanout/room")
                .header("Authorization", bearer("im.realtime.internal"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    private String bearer(String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtCodecs.resolvedIssuer(jwtProperties))
                .subject("security-test")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtEncoder encoder = JwtCodecs.jwtEncoder(jwtProperties);
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
