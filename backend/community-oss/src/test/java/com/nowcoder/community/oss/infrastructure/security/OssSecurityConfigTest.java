package com.nowcoder.community.oss.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        InternalOssObjectController.class,
        OssObjectController.class,
        PublicFileController.class
}, properties = {
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
@Import({
        InternalOssObjectController.class,
        OssObjectController.class,
        PublicFileController.class,
        OssSecurityConfig.class,
        SecurityExceptionHandler.class,
        OssSecurityConfigTest.WebMvcSliceJsonCodecTestConfig.class
})
class OssSecurityConfigTest {

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

    @MockBean
    private JwtDecoder jwtDecoder;

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

    @Test
    void ossApiShouldRequireJwtAndAllowAuthenticatedRequests() throws Exception {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        UUID sessionId = uuid(3);
        when(uploadService.prepareUpload(any())).thenReturn(new ObjectUploadSessionResult(
                sessionId,
                objectId,
                versionId,
                "PROXY",
                "/api/oss/objects/" + objectId + "/complete",
                Instant.parse("2026-05-07T00:15:00Z")
        ));

        mvc.perform(post("/api/oss/objects/upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadSessionPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mvc.perform(post("/api/oss/objects/upload-sessions")
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(9).toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadSessionPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));
    }

    @Test
    void publicFilesShouldRemainAnonymous() throws Exception {
        mvc.perform(get("/files/not-present"))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalOssReferenceApiShouldRequireJwt() throws Exception {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        UUID referenceId = uuid(3);
        when(referenceService.bindReference(any())).thenReturn(new ObjectReferenceResult(
                referenceId,
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

        mvc.perform(post("/internal/oss/objects/{objectId}/references", objectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referencePayload(versionId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mvc.perform(post("/internal/oss/objects/{objectId}/references", objectId)
                        .with(jwt().jwt(jwt -> jwt.subject(uuid(9).toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referencePayload(versionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
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
