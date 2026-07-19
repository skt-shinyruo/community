package com.nowcoder.community.oss.controller;

import com.nowcoder.community.oss.application.ObjectAccessApplicationService;
import com.nowcoder.community.oss.application.ObjectLifecycleApplicationService;
import com.nowcoder.community.oss.application.ObjectPermissionApplicationService;
import com.nowcoder.community.oss.application.ObjectQueryApplicationService;
import com.nowcoder.community.oss.application.ObjectUploadApplicationService;
import com.nowcoder.community.oss.application.command.CompleteObjectUploadCommand;
import com.nowcoder.community.oss.application.command.CreateSignedUrlCommand;
import com.nowcoder.community.oss.application.command.DeleteObjectCommand;
import com.nowcoder.community.oss.application.command.GrantObjectAccessCommand;
import com.nowcoder.community.oss.application.command.PrepareObjectUploadCommand;
import com.nowcoder.community.oss.application.command.RevokeObjectAccessCommand;
import com.nowcoder.community.oss.application.result.ObjectAccessDecisionResult;
import com.nowcoder.community.oss.application.result.ObjectDownloadResult;
import com.nowcoder.community.oss.application.result.ObjectLifecycleResult;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.application.result.ObjectSignedUrlResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OssObjectControllerTest {

    private static final String USER_SUBJECT = uuid(17).toString();
    private static final String ATTACKER = uuid(99).toString();

    @Test
    void prepareUploadShouldIgnoreUserControlledOwnerAndActor() throws Exception {
        ObjectUploadApplicationService uploadService = mock(ObjectUploadApplicationService.class);
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        UUID sessionId = uuid(3);
        when(uploadService.prepareUpload(any())).thenReturn(new ObjectUploadSessionResult(
                sessionId, objectId, versionId, "PROXY",
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
                        .principal(userAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usage": "USER_AVATAR",
                                  "ownerService": "community-app",
                                  "ownerDomain": "user",
                                  "ownerType": "SERVICE",
                                  "ownerId": "%s",
                                  "visibility": "PUBLIC",
                                  "fileName": "avatar.png",
                                  "contentType": "image/png",
                                  "contentLength": 6,
                                  "checksumSha256": "sha256-avatar",
                                  "actorId": "%s"
                                }
                                """.formatted(ATTACKER, ATTACKER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));

        ArgumentCaptor<PrepareObjectUploadCommand> captor = ArgumentCaptor.forClass(PrepareObjectUploadCommand.class);
        verify(uploadService).prepareUpload(captor.capture());
        assertThat(captor.getValue().ownerType()).isEqualTo("USER");
        assertThat(captor.getValue().ownerId()).isEqualTo(USER_SUBJECT);
        assertThat(captor.getValue().actorId()).isEqualTo(USER_SUBJECT);
    }

    @Test
    void completeUploadShouldUseAuthenticatedActor() throws Exception {
        ObjectUploadApplicationService uploadService = mock(ObjectUploadApplicationService.class);
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        UUID sessionId = uuid(3);
        when(uploadService.completeUpload(any())).thenReturn(metadata(objectId, versionId));
        MockMvc mvc = mockMvc(
                uploadService,
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                mock(ObjectPermissionApplicationService.class),
                mock(ObjectLifecycleApplicationService.class)
        );

        mvc.perform(multipart("/api/oss/objects/{objectId}/complete", objectId)
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", "avatar".getBytes()))
                        .param("sessionId", sessionId.toString())
                        .param("versionId", versionId.toString())
                        .param("checksumSha256", "sha256-avatar")
                        .param("actorId", ATTACKER)
                        .principal(userAuthentication()))
                .andExpect(status().isOk());

        ArgumentCaptor<CompleteObjectUploadCommand> captor = ArgumentCaptor.forClass(CompleteObjectUploadCommand.class);
        verify(uploadService).completeUpload(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo(USER_SUBJECT);
    }

    @Test
    void getMetadataShouldUseAuthenticatedActor() throws Exception {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        ObjectMetadataResult metadata = metadata(objectId, versionId);
        ObjectQueryApplicationService queryService = mock(ObjectQueryApplicationService.class);
        when(queryService.getMetadata(objectId, USER_SUBJECT)).thenReturn(metadata);
        MockMvc mvc = mockMvc(
                mock(ObjectUploadApplicationService.class),
                queryService,
                mock(ObjectAccessApplicationService.class),
                mock(ObjectPermissionApplicationService.class),
                mock(ObjectLifecycleApplicationService.class)
        );

        mvc.perform(get("/api/oss/objects/{objectId}", objectId)
                        .queryParam("actorId", ATTACKER)
                        .principal(userAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value(objectId.toString()));

        verify(queryService).getMetadata(objectId, USER_SUBJECT);
    }

    @Test
    void signedUrlShouldUseAuthenticatedActor() throws Exception {
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

        mvc.perform(get("/api/oss/objects/{objectId}/signed-url", objectId)
                        .queryParam("ttlSeconds", "300")
                        .queryParam("actorId", ATTACKER)
                        .principal(userAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://localhost:12880/files/signed"));

        ArgumentCaptor<CreateSignedUrlCommand> captor = ArgumentCaptor.forClass(CreateSignedUrlCommand.class);
        verify(accessService).createSignedDownloadUrl(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo(USER_SUBJECT);
    }

    @Test
    void grantAccessShouldIgnoreUserControlledActor() throws Exception {
        UUID objectId = uuid(1);
        UUID grantId = uuid(4);
        ObjectPermissionApplicationService permissionService = mock(ObjectPermissionApplicationService.class);
        when(permissionService.grantAccess(any())).thenReturn(accessDecision(grantId, objectId));
        MockMvc mvc = mockMvc(
                mock(ObjectUploadApplicationService.class),
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                permissionService,
                mock(ObjectLifecycleApplicationService.class)
        );

        mvc.perform(post("/api/oss/objects/{objectId}/grants", objectId)
                        .principal(userAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionId": "%s",
                                  "principalType": "USER",
                                  "principalValue": "7",
                                  "permission": "READ",
                                  "expiresAt": "2026-05-07T01:00:00Z",
                                  "actorId": "%s"
                                }
                                """.formatted(uuid(2), ATTACKER)))
                .andExpect(status().isOk());

        ArgumentCaptor<GrantObjectAccessCommand> captor = ArgumentCaptor.forClass(GrantObjectAccessCommand.class);
        verify(permissionService).grantAccess(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo(USER_SUBJECT);
    }

    @Test
    void revokeAccessShouldIgnoreUserControlledActor() throws Exception {
        UUID objectId = uuid(1);
        UUID grantId = uuid(4);
        ObjectPermissionApplicationService permissionService = mock(ObjectPermissionApplicationService.class);
        when(permissionService.revokeAccess(any())).thenReturn(accessDecision(grantId, objectId));
        MockMvc mvc = mockMvc(
                mock(ObjectUploadApplicationService.class),
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                permissionService,
                mock(ObjectLifecycleApplicationService.class)
        );

        mvc.perform(delete("/api/oss/objects/{objectId}/grants/{grantId}", objectId, grantId)
                        .queryParam("actorId", ATTACKER)
                        .principal(userAuthentication()))
                .andExpect(status().isOk());

        ArgumentCaptor<RevokeObjectAccessCommand> captor = ArgumentCaptor.forClass(RevokeObjectAccessCommand.class);
        verify(permissionService).revokeAccess(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo(USER_SUBJECT);
    }

    @Test
    void deleteObjectShouldIgnoreUserControlledActor() throws Exception {
        UUID objectId = uuid(1);
        ObjectLifecycleApplicationService lifecycleService = mock(ObjectLifecycleApplicationService.class);
        when(lifecycleService.deleteObject(any())).thenReturn(new ObjectLifecycleResult(
                objectId, uuid(2), "PURGED", false, true, "object purged",
                Instant.parse("2026-05-07T00:10:00Z")
        ));
        MockMvc mvc = mockMvc(
                mock(ObjectUploadApplicationService.class),
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                mock(ObjectPermissionApplicationService.class),
                lifecycleService
        );

        mvc.perform(delete("/api/oss/objects/{objectId}", objectId)
                        .queryParam("actorId", ATTACKER)
                        .principal(userAuthentication()))
                .andExpect(status().isOk());

        ArgumentCaptor<DeleteObjectCommand> captor = ArgumentCaptor.forClass(DeleteObjectCommand.class);
        verify(lifecycleService).deleteObject(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo(USER_SUBJECT);
    }

    @Test
    void publicFileDownloadShouldPreserveContentHeaders() throws Exception {
        ObjectQueryApplicationService queryService = mock(ObjectQueryApplicationService.class);
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        when(queryService.resolvePublicFile(objectId + "/" + versionId + "/avatar.png")).thenReturn(new ObjectDownloadResult(
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

        mvc.perform(get("/files/" + objectId + "/" + versionId + "/avatar.png"))
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

    private static UsernamePasswordAuthenticationToken userAuthentication() {
        return new UsernamePasswordAuthenticationToken(USER_SUBJECT, "n/a", List.of());
    }

    private static ObjectMetadataResult metadata(UUID objectId, UUID versionId) {
        return new ObjectMetadataResult(
                objectId, versionId, "USER_AVATAR", "community-app", "user",
                "USER", USER_SUBJECT, "PUBLIC", "ACTIVE", "avatar.png",
                "image/png", 6, "sha256-avatar",
                "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
        );
    }

    private static ObjectAccessDecisionResult accessDecision(UUID grantId, UUID objectId) {
        return new ObjectAccessDecisionResult(
                grantId, objectId, uuid(2), "USER", "7", "READ",
                Instant.parse("2026-05-07T01:00:00Z"), USER_SUBJECT,
                Instant.parse("2026-05-07T00:00:00Z"), null, true
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
