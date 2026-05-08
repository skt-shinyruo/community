package com.nowcoder.community.oss.controller;

import com.nowcoder.community.oss.application.ObjectAccessApplicationService;
import com.nowcoder.community.oss.application.ObjectLifecycleApplicationService;
import com.nowcoder.community.oss.application.ObjectPermissionApplicationService;
import com.nowcoder.community.oss.application.ObjectQueryApplicationService;
import com.nowcoder.community.oss.application.ObjectUploadApplicationService;
import com.nowcoder.community.oss.application.result.ObjectAccessDecisionResult;
import com.nowcoder.community.oss.application.result.ObjectDownloadResult;
import com.nowcoder.community.oss.application.result.ObjectLifecycleResult;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.application.result.ObjectSignedUrlResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OssObjectControllerTest {

    @Test
    void createUploadSessionShouldReturnPreparedSession() throws Exception {
        ObjectUploadApplicationService uploadService = mock(ObjectUploadApplicationService.class);
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
        MockMvc mvc = mockMvc(
                uploadService,
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                mock(ObjectPermissionApplicationService.class),
                mock(ObjectLifecycleApplicationService.class)
        );

        mvc.perform(post("/api/oss/objects/upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usage": "USER_AVATAR",
                                  "ownerService": "community-app",
                                  "ownerDomain": "user",
                                  "ownerType": "avatar",
                                  "ownerId": "7",
                                  "visibility": "PUBLIC",
                                  "fileName": "avatar.png",
                                  "contentType": "image/png",
                                  "contentLength": 6,
                                  "checksumSha256": "sha256-avatar",
                                  "aliasKey": "avatar/7/0123456789abcdef0123456789abcdef",
                                  "actorId": "7"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.objectId").value(objectId.toString()))
                .andExpect(jsonPath("$.versionId").value(versionId.toString()))
                .andExpect(jsonPath("$.uploadMode").value("PROXY"))
                .andExpect(jsonPath("$.uploadUrl").value("/api/oss/objects/" + objectId + "/complete"));
    }

    @Test
    void getMetadataShouldReturnObjectDetails() throws Exception {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        ObjectQueryApplicationService queryService = mock(ObjectQueryApplicationService.class);
        when(queryService.getMetadata(objectId)).thenReturn(new ObjectMetadataResult(
                objectId,
                versionId,
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "ACTIVE",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
        ));
        MockMvc mvc = mockMvc(
                mock(ObjectUploadApplicationService.class),
                queryService,
                mock(ObjectAccessApplicationService.class),
                mock(ObjectPermissionApplicationService.class),
                mock(ObjectLifecycleApplicationService.class)
        );

        mvc.perform(get("/api/oss/objects/{objectId}", objectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value(objectId.toString()))
                .andExpect(jsonPath("$.currentVersionId").value(versionId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.publicUrl").value("http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"));
    }

    @Test
    void signedUrlShouldReturnUrlAndExpiry() throws Exception {
        UUID objectId = uuid(1);
        ObjectAccessApplicationService accessService = mock(ObjectAccessApplicationService.class);
        when(accessService.createSignedDownloadUrl(any())).thenReturn(new ObjectSignedUrlResult(
                "http://localhost:12880/files/signed",
                "GET",
                Instant.parse("2026-05-07T00:05:00Z"),
                "private, max-age=300"
        ));
        MockMvc mvc = mockMvc(
                mock(ObjectUploadApplicationService.class),
                mock(ObjectQueryApplicationService.class),
                accessService,
                mock(ObjectPermissionApplicationService.class),
                mock(ObjectLifecycleApplicationService.class)
        );

        mvc.perform(get("/api/oss/objects/{objectId}/signed-url?ttlSeconds=300", objectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://localhost:12880/files/signed"))
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.cacheControl").value("private, max-age=300"));
    }

    @Test
    void grantAccessShouldReturnGrantDecision() throws Exception {
        UUID objectId = uuid(1);
        UUID grantId = uuid(4);
        ObjectPermissionApplicationService permissionService = mock(ObjectPermissionApplicationService.class);
        when(permissionService.grantAccess(any())).thenReturn(new ObjectAccessDecisionResult(
                grantId,
                objectId,
                uuid(2),
                "USER",
                "7",
                "READ",
                Instant.parse("2026-05-07T01:00:00Z"),
                "7",
                Instant.parse("2026-05-07T00:00:00Z"),
                null,
                true
        ));
        MockMvc mvc = mockMvc(
                mock(ObjectUploadApplicationService.class),
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                permissionService,
                mock(ObjectLifecycleApplicationService.class)
        );

        mvc.perform(post("/api/oss/objects/{objectId}/grants", objectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionId": "%s",
                                  "principalType": "USER",
                                  "principalValue": "7",
                                  "permission": "READ",
                                  "expiresAt": "2026-05-07T01:00:00Z",
                                  "actorId": "7"
                                }
                                """.formatted(uuid(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantId").value(grantId.toString()))
                .andExpect(jsonPath("$.permission").value("READ"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deleteObjectShouldReturnLifecycleResult() throws Exception {
        UUID objectId = uuid(1);
        ObjectLifecycleApplicationService lifecycleService = mock(ObjectLifecycleApplicationService.class);
        when(lifecycleService.deleteObject(any())).thenReturn(new ObjectLifecycleResult(
                objectId,
                uuid(2),
                "PURGED",
                false,
                true,
                "object purged",
                Instant.parse("2026-05-07T00:10:00Z")
        ));
        MockMvc mvc = mockMvc(
                mock(ObjectUploadApplicationService.class),
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                mock(ObjectPermissionApplicationService.class),
                lifecycleService
        );

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/oss/objects/{objectId}", objectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PURGED"))
                .andExpect(jsonPath("$.purged").value(true));
    }

    @Test
    void publicFileDownloadShouldPreserveContentHeaders() throws Exception {
        ObjectQueryApplicationService queryService = mock(ObjectQueryApplicationService.class);
        when(queryService.resolvePublicFile("avatar/7/0123456789abcdef0123456789abcdef")).thenReturn(new ObjectDownloadResult(
                new ByteArrayInputStream("avatar".getBytes()),
                "image/png",
                6,
                "etag-1",
                "public, max-age=31536000, immutable",
                "avatar.png"
        ));
        MockMvc mvc = mockMvc(
                mock(ObjectUploadApplicationService.class),
                queryService,
                mock(ObjectAccessApplicationService.class),
                mock(ObjectPermissionApplicationService.class),
                mock(ObjectLifecycleApplicationService.class)
        );

        mvc.perform(get("/files/avatar/7/0123456789abcdef0123456789abcdef"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("ETag", "\"etag-1\""))
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
                .andExpect(content().string("avatar"));
    }

    private MockMvc mockMvc(
            ObjectUploadApplicationService uploadService,
            ObjectQueryApplicationService queryService,
            ObjectAccessApplicationService accessService,
            ObjectPermissionApplicationService permissionService,
            ObjectLifecycleApplicationService lifecycleService
    ) {
        return MockMvcBuilders.standaloneSetup(
                new OssObjectController(uploadService, queryService, accessService, permissionService, lifecycleService),
                new PublicFileController(queryService)
        ).build();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
