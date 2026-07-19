package com.nowcoder.community.oss.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.security.autoconfig.SecurityCommonAutoConfiguration;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.oss.application.ObjectAccessApplicationService;
import com.nowcoder.community.oss.application.ObjectLifecycleApplicationService;
import com.nowcoder.community.oss.application.ObjectPermissionApplicationService;
import com.nowcoder.community.oss.application.ObjectQueryApplicationService;
import com.nowcoder.community.oss.application.ObjectReferenceApplicationService;
import com.nowcoder.community.oss.application.ObjectUploadApplicationService;
import com.nowcoder.community.oss.application.result.ObjectReferenceResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import com.nowcoder.community.oss.controller.InternalOssObjectController;
import com.nowcoder.community.oss.controller.OssObjectController;
import com.nowcoder.community.oss.controller.PublicFileController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        InternalOssObjectController.class,
        OssObjectController.class,
        PublicFileController.class,
        OssSecurityConfigTest.SecurityProbeController.class
}, properties = {
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "security.jwt.hmac-secret=01234567890123456789012345678901",
        "security.jwt.issuer=community-auth",
        "oss.security.service-jwt.issuer=community-auth",
        "oss.security.service-jwt.audience=community-oss",
        "oss.security.service-jwt.scope=oss.internal",
        "METRICS_BASIC_AUTH_USERNAME=metrics-reader",
        "METRICS_BASIC_AUTH_PASSWORD=metrics-password-123"
})
@Import({
        InternalOssObjectController.class,
        OssObjectController.class,
        PublicFileController.class,
        OssSecurityConfigTest.SecurityProbeController.class,
        OssSecurityConfig.class,
        SecurityCommonAutoConfiguration.class,
        SecurityExceptionHandler.class,
        OssSecurityConfigTest.WebMvcSliceJsonCodecTestConfig.class
})
class OssSecurityConfigTest {

    private static final String JWT_SECRET = "01234567890123456789012345678901";
    private static final String INVALID_JWT_SECRET = "98765432109876543210987654321098";
    private static final String USER_ISSUER = "community-auth";
    private static final String SERVICE_AUDIENCE = "community-oss";
    private static final String SERVICE_SCOPE = "oss.internal";
    private static final String METRICS_USERNAME = "metrics-reader";
    private static final String METRICS_PASSWORD = "metrics-password-123";
    private static final String TRACE_ID = "abcdefabcdefabcdefabcdefabcdefab";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-1234567890abcdef-01";
    private static final UUID USER_ID = uuid(9);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ObjectUploadApplicationService uploadService;

    @MockBean
    private ObjectQueryApplicationService queryService;

    @MockBean
    private ObjectAccessApplicationService accessService;

    @MockBean
    private ObjectPermissionApplicationService permissionService;

    @MockBean
    private ObjectLifecycleApplicationService lifecycleService;

    @MockBean
    private ObjectReferenceApplicationService referenceService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class WebMvcSliceJsonCodecTestConfig {

        @Bean
        JsonCodec jsonCodec(ObjectMapper objectMapper) {
            return new JacksonJsonCodec(objectMapper);
        }
    }

    @RestController
    static class SecurityProbeController {

        @GetMapping({
                "/actuator/health",
                "/actuator/info",
                "/actuator/prometheus",
                "/actuator/env",
                "/unmatched-security-probe"
        })
        String probe() {
            return "ok";
        }
    }

    @BeforeEach
    void setUpApplicationResults() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        when(uploadService.prepareUpload(any())).thenReturn(new ObjectUploadSessionResult(
                uuid(3),
                objectId,
                versionId,
                "PROXY",
                "/api/oss/objects/" + objectId + "/complete",
                Instant.parse("2026-05-07T00:15:00Z")
        ));
        when(referenceService.bindReference(any())).thenReturn(new ObjectReferenceResult(
                uuid(4),
                objectId,
                versionId,
                "community-app",
                "drive",
                "drive-entry",
                "7",
                "PRIMARY",
                "ACTIVE",
                null,
                Instant.parse("2026-05-07T00:00:00Z"),
                null
        ));
    }

    @Test
    void anonymousUserAndInternalApisShouldReturnUnauthorizedEnvelope() throws Exception {
        expectSecurityError(mvc.perform(userApiRequest(null)), 401);
        expectSecurityError(mvc.perform(internalApiRequest(null)), 401);
    }

    @Test
    void validUserJwtShouldAccessUserApiButNotInternalApi() throws Exception {
        String token = token(USER_ISSUER, USER_ID.toString(), null, null, futureExpiry());

        mvc.perform(userApiRequest(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value(uuid(1).toString()));
        expectSecurityError(mvc.perform(internalApiRequest(token)), 401);
    }

    @Test
    void validServiceJwtShouldAccessInternalApiButNotUserApi() throws Exception {
        String serviceToken = token(
                USER_ISSUER,
                "community-app",
                SERVICE_AUDIENCE,
                SERVICE_SCOPE,
                futureExpiry()
        );
        String uuidSubjectServiceToken = token(
                USER_ISSUER,
                USER_ID.toString(),
                SERVICE_AUDIENCE,
                SERVICE_SCOPE,
                futureExpiry()
        );

        mvc.perform(internalApiRequest(serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        expectSecurityError(mvc.perform(userApiRequest(uuidSubjectServiceToken)), 403);
    }

    @Test
    void internalApiShouldRejectWrongAudience() throws Exception {
        String token = token(USER_ISSUER, "community-app", "community-search", SERVICE_SCOPE, futureExpiry());

        expectSecurityError(mvc.perform(internalApiRequest(token)), 401);
    }

    @Test
    void internalApiShouldForbidMissingServiceScope() throws Exception {
        String token = token(USER_ISSUER, "community-app", SERVICE_AUDIENCE, null, futureExpiry());

        expectSecurityError(mvc.perform(internalApiRequest(token)), 403);
    }

    @Test
    void internalApiShouldRejectWrongIssuer() throws Exception {
        String token = token("other-issuer", "community-app", SERVICE_AUDIENCE, SERVICE_SCOPE, futureExpiry());

        expectSecurityError(mvc.perform(internalApiRequest(token)), 401);
    }

    @Test
    void internalApiShouldRejectExpiredServiceToken() throws Exception {
        String token = token(
                USER_ISSUER,
                "community-app",
                SERVICE_AUDIENCE,
                SERVICE_SCOPE,
                Instant.now().minusSeconds(300)
        );

        expectSecurityError(mvc.perform(internalApiRequest(token)), 401);
    }

    @Test
    void userTokenWithIncidentalServiceScopeShouldRemainAUserIdentity() throws Exception {
        String token = token(USER_ISSUER, USER_ID.toString(), null, SERVICE_SCOPE, futureExpiry());

        mvc.perform(userApiRequest(token))
                .andExpect(status().isOk());
        expectSecurityError(mvc.perform(internalApiRequest(token)), 401);
    }

    @Test
    void internalApiShouldForbidBlankServiceSubject() throws Exception {
        String token = token(USER_ISSUER, "   ", SERVICE_AUDIENCE, SERVICE_SCOPE, futureExpiry());

        expectSecurityError(mvc.perform(internalApiRequest(token)), 403);
    }

    @Test
    void userApiShouldForbidNonUuidSubject() throws Exception {
        String token = token(USER_ISSUER, "community-app", null, null, futureExpiry());

        expectSecurityError(mvc.perform(userApiRequest(token)), 403);
    }

    @Test
    void userApiShouldRejectJwtWithInvalidSignature() throws Exception {
        String token = token(
                USER_ISSUER,
                USER_ID.toString(),
                null,
                null,
                futureExpiry(),
                INVALID_JWT_SECRET
        );

        expectSecurityError(mvc.perform(userApiRequest(token)), 401);
    }

    @Test
    void internalApiShouldRejectJwtWithInvalidSignature() throws Exception {
        String token = token(
                USER_ISSUER,
                "community-app",
                SERVICE_AUDIENCE,
                SERVICE_SCOPE,
                futureExpiry(),
                INVALID_JWT_SECRET
        );

        expectSecurityError(mvc.perform(internalApiRequest(token)), 401);
    }

    @Test
    void healthAndInfoShouldRemainAnonymous() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusShouldRejectAnonymousAndInvalidBasicAuth() throws Exception {
        expectSecurityError(mvc.perform(securityProbeRequest("/actuator/prometheus")), 401);
        expectSecurityError(mvc.perform(securityProbeRequest("/actuator/prometheus")
                .with(httpBasic(METRICS_USERNAME, "wrong-password"))), 401);
    }

    @Test
    void prometheusShouldAcceptConfiguredBasicAuth() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                        .with(httpBasic(METRICS_USERNAME, METRICS_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void nonPublicActuatorEndpointShouldRemainDenied() throws Exception {
        expectSecurityError(mvc.perform(securityProbeRequest("/actuator/env")
                .with(httpBasic(METRICS_USERNAME, METRICS_PASSWORD))), 403);
    }

    @Test
    void unmatchedRouteShouldBeDeniedByFallbackChain() throws Exception {
        expectSecurityError(mvc.perform(securityProbeRequest("/unmatched-security-probe")), 401);
    }

    @Test
    void publicFilesShouldRemainAnonymous() throws Exception {
        mvc.perform(get("/files/not-present"))
                .andExpect(status().isNotFound());
    }

    private ResultActions expectSecurityError(ResultActions result, int expectedStatus) throws Exception {
        return result
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(expectedStatus))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(header().exists(TraceHeaders.HEADER_TRACEPARENT))
                .andExpect(response -> assertThat(response.getResponse().getHeader(TraceHeaders.HEADER_TRACEPARENT))
                        .matches("00-" + TRACE_ID + "-[0-9a-f]{16}-01"));
    }

    private MockHttpServletRequestBuilder userApiRequest(String token) {
        return withBearer(post("/api/oss/objects/upload-sessions")
                .header(TraceHeaders.HEADER_TRACEPARENT, TRACEPARENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadSessionPayload()), token);
    }

    private MockHttpServletRequestBuilder internalApiRequest(String token) {
        return withBearer(post("/internal/oss/objects/{objectId}/references", uuid(1))
                .header(TraceHeaders.HEADER_TRACEPARENT, TRACEPARENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(referencePayload(uuid(2))), token);
    }

    private MockHttpServletRequestBuilder securityProbeRequest(String path) {
        return get(path).header(TraceHeaders.HEADER_TRACEPARENT, TRACEPARENT);
    }

    private MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request;
    }

    private String token(String issuer, String subject, String audience, String scope, Instant expiresAt) throws Exception {
        return token(issuer, subject, audience, scope, expiresAt, JWT_SECRET);
    }

    private String token(
            String issuer,
            String subject,
            String audience,
            String scope,
            Instant expiresAt,
            String secret
    ) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(expiresAt));
        if (audience != null) {
            claims.audience(List.of(audience));
        }
        if (scope != null) {
            claims.claim("scope", scope);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private Instant futureExpiry() {
        return Instant.now().plusSeconds(300);
    }

    private static String uploadSessionPayload() {
        return """
                {
                  "usage": "DRIVE_FILE",
                  "ownerService": "community-app",
                  "ownerDomain": "drive",
                  "ownerType": "drive-upload",
                  "ownerId": "7",
                  "visibility": "PRIVATE",
                  "fileName": "report.pdf",
                  "contentType": "application/pdf",
                  "contentLength": 6,
                  "checksumSha256": "",
                  "actorId": "7"
                }
                """;
    }

    private static String referencePayload(UUID versionId) {
        return """
                {
                  "versionId": "%s",
                  "subjectService": "community-app",
                  "subjectDomain": "drive",
                  "subjectType": "drive-entry",
                  "subjectId": "7",
                  "referenceRole": "PRIMARY",
                  "retainUntil": "2026-05-07T01:00:00Z",
                  "actorId": "7"
                }
                """.formatted(versionId);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
