package com.nowcoder.community.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayErrorContractTest.TestErrorTriggerConfiguration.class)
class GatewayErrorContractTest {

    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${security.jwt.hmac-secret}")
    private String hmacSecret;

    @Test
    void unauthorizedShouldReturnResultAndTraceId() throws Exception {
        EntityExchangeResult<byte[]> result = webTestClient.get()
                .uri("/api/ops/ping")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals("X-Trace-Id", TRACE_ID)
                .expectBody().jsonPath("$.code").isEqualTo(401)
                .jsonPath("$.traceId").isEqualTo(TRACE_ID)
                .jsonPath("$.timestamp").exists()
                .returnResult();

        assertThat(result.getResponseHeaders().getFirst("traceparent")).isNotBlank();
    }

    @Test
    void forbiddenShouldReturnResultAndTraceId() throws Exception {
        EntityExchangeResult<byte[]> result = webTestClient.get()
                .uri("/api/ops/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_USER")))
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals("X-Trace-Id", TRACE_ID)
                .expectBody().jsonPath("$.code").isEqualTo(403)
                .jsonPath("$.traceId").isEqualTo(TRACE_ID)
                .jsonPath("$.timestamp").exists()
                .returnResult();

        assertThat(result.getResponseHeaders().getFirst("traceparent")).isNotBlank();
    }

    @Test
    void badRequestShouldReturn400Result() throws Exception {
        assertErrorContract("/__test__/bad_request", HttpStatus.BAD_REQUEST, 400);
    }

    @Test
    void tooManyRequestsShouldReturn429Result() throws Exception {
        assertErrorContract("/__test__/too_many", HttpStatus.TOO_MANY_REQUESTS, 429);
    }

    @Test
    void internalErrorShouldReturn500Result() throws Exception {
        assertErrorContract("/__test__/boom", HttpStatus.INTERNAL_SERVER_ERROR, 500);
    }

    private void assertErrorContract(String uri, HttpStatus expectedStatus, int expectedCode) throws Exception {
        EntityExchangeResult<byte[]> result = webTestClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_ADMIN")))
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals("X-Trace-Id", TRACE_ID)
                .expectBody(byte[].class)
                .returnResult();

        String body = new String(result.getResponseBodyContent(), StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("code").asInt()).isEqualTo(expectedCode);
        assertThat(json.path("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(json.path("timestamp").asLong()).isPositive();
        assertThat(result.getResponseHeaders().getFirst("traceparent")).isNotBlank();
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

    @TestConfiguration
    static class TestErrorTriggerConfiguration {

        @Bean
        WebFilter errorTriggerWebFilter() {
            return new ErrorTriggerWebFilter();
        }

        private static class ErrorTriggerWebFilter implements WebFilter, Ordered {

            @Override
            public int getOrder() {
                // TraceIdWebFilter 需要先执行，确保 error handler 中可读到规范化 traceId。
                return Ordered.HIGHEST_PRECEDENCE + 1;
            }

            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                String path = exchange == null || exchange.getRequest() == null || exchange.getRequest().getURI() == null
                        ? ""
                        : exchange.getRequest().getURI().getPath();

                return switch (path) {
                    case "/__test__/bad_request" -> Mono.error(new IllegalArgumentException("bad request"));
                    case "/__test__/too_many" -> Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limited"));
                    case "/__test__/boom" -> Mono.error(new RuntimeException("boom"));
                    default -> chain.filter(exchange);
                };
            }
        }
    }
}
