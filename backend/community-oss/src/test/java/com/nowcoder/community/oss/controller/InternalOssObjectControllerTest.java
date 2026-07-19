package com.nowcoder.community.oss.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorKind;
import com.nowcoder.community.common.exception.SimpleErrorCode;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.oss.application.ObjectReferenceApplicationService;
import com.nowcoder.community.oss.application.ObjectAccessApplicationService;
import com.nowcoder.community.oss.application.ObjectLifecycleApplicationService;
import com.nowcoder.community.oss.application.ObjectQueryApplicationService;
import com.nowcoder.community.oss.application.ObjectUploadApplicationService;
import com.nowcoder.community.oss.application.command.BindObjectReferenceCommand;
import com.nowcoder.community.oss.application.command.CompleteObjectUploadCommand;
import com.nowcoder.community.oss.application.command.CreateSignedUrlCommand;
import com.nowcoder.community.oss.application.command.DeleteObjectCommand;
import com.nowcoder.community.oss.application.command.PrepareObjectUploadCommand;
import com.nowcoder.community.oss.application.command.ReleaseObjectReferenceCommand;
import com.nowcoder.community.oss.application.result.ObjectLifecycleResult;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.application.result.ObjectReferenceResult;
import com.nowcoder.community.oss.application.result.ObjectSignedUrlResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalOssObjectControllerTest {

    private static final String SERVICE_SUBJECT = "community-app";

    @Test
    void internalCapabilityBoundaryShouldExposeIndependentDtoAndApplicationEntrypoints() {
        assertClassExists("com.nowcoder.community.oss.controller.dto.InternalPrepareUploadSessionRequest");
        assertThat(Arrays.stream(InternalOssObjectController.class.getConstructors())
                .anyMatch(constructor -> Arrays.equals(constructor.getParameterTypes(), new Class<?>[]{
                        ObjectUploadApplicationService.class,
                        ObjectQueryApplicationService.class,
                        ObjectAccessApplicationService.class,
                        ObjectReferenceApplicationService.class,
                        ObjectLifecycleApplicationService.class
                }))).isTrue();
        assertMethodExists(ObjectUploadApplicationService.class, "prepareInternalUpload",
                String.class, PrepareObjectUploadCommand.class);
        assertMethodExists(ObjectUploadApplicationService.class, "completeInternalUpload",
                String.class, CompleteObjectUploadCommand.class);
        assertMethodExists(ObjectQueryApplicationService.class, "getInternalMetadata",
                UUID.class, String.class);
        assertMethodExists(ObjectAccessApplicationService.class, "createInternalSignedDownloadUrl",
                CreateSignedUrlCommand.class, String.class);
        assertMethodExists(ObjectLifecycleApplicationService.class, "deleteInternalObject",
                DeleteObjectCommand.class, String.class);
        assertMethodExists(ObjectReferenceApplicationService.class, "bindInternalReference",
                String.class, BindObjectReferenceCommand.class);
        assertMethodExists(ObjectReferenceApplicationService.class, "getInternalReference",
                UUID.class, UUID.class, String.class);
        assertMethodExists(ObjectReferenceApplicationService.class, "releaseInternalReference",
                String.class, ReleaseObjectReferenceCommand.class);
    }

    @Test
    void prepareShouldUseServiceSubjectAndPreserveOwnerDomainFacts() throws Exception {
        UUID sessionId = uuid(3);
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        ObjectUploadApplicationService uploadService = mock(ObjectUploadApplicationService.class);
        when(uploadService.prepareInternalUpload(eq(SERVICE_SUBJECT), any())).thenReturn(
                new ObjectUploadSessionResult(
                        sessionId,
                        objectId,
                        versionId,
                        "PROXY",
                        "/internal/oss/upload-sessions/" + sessionId + "/complete",
                        Instant.parse("2026-05-07T00:15:00Z")
                ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(uploadService,
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                mock(ObjectReferenceApplicationService.class),
                mock(ObjectLifecycleApplicationService.class))).build();

        mvc.perform(post("/internal/oss/upload-sessions")
                        .principal(serviceAuthentication(SERVICE_SUBJECT))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "usage": "DRIVE_FILE",
                                  "ownerService": "community-app",
                                  "ownerDomain": "drive",
                                  "ownerType": "drive-upload",
                                  "ownerId": "upload-7",
                                  "visibility": "PRIVATE",
                                  "fileName": "note.txt",
                                  "contentType": "text/plain",
                                  "contentLength": 2,
                                  "checksumSha256": "sha256-note",
                                  "actorId": "user-7"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));

        ArgumentCaptor<PrepareObjectUploadCommand> command =
                ArgumentCaptor.forClass(PrepareObjectUploadCommand.class);
        verify(uploadService).prepareInternalUpload(eq(SERVICE_SUBJECT), command.capture());
        assertThat(command.getValue().ownerService()).isEqualTo(SERVICE_SUBJECT);
        assertThat(command.getValue().ownerType()).isEqualTo("drive-upload");
        assertThat(command.getValue().ownerId()).isEqualTo("upload-7");
        assertThat(command.getValue().actorId()).isEqualTo("user-7");
    }

    @Test
    void completeShouldUseServiceSubjectAndConvertMultipartContent() throws Exception {
        UUID sessionId = uuid(3);
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        byte[] content = "note".getBytes(StandardCharsets.UTF_8);
        ObjectUploadApplicationService uploadService = mock(ObjectUploadApplicationService.class);
        when(uploadService.completeInternalUpload(eq(SERVICE_SUBJECT), any())).thenReturn(
                new ObjectMetadataResult(
                        objectId,
                        versionId,
                        "DRIVE_FILE",
                        SERVICE_SUBJECT,
                        "drive",
                        "DRIVE_UPLOAD",
                        "upload-7",
                        "PRIVATE",
                        "ACTIVE",
                        "note.txt",
                        "text/plain",
                        content.length,
                        "sha256-note",
                        ""
                ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(uploadService,
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                mock(ObjectReferenceApplicationService.class),
                mock(ObjectLifecycleApplicationService.class))).build();

        mvc.perform(multipart("/internal/oss/upload-sessions/{sessionId}/complete", sessionId)
                        .file(new MockMultipartFile("file", "note.txt", "text/plain", content))
                        .param("objectId", objectId.toString())
                        .param("versionId", versionId.toString())
                        .param("checksumSha256", "sha256-note")
                        .principal(serviceAuthentication(SERVICE_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value(objectId.toString()));

        ArgumentCaptor<CompleteObjectUploadCommand> command =
                ArgumentCaptor.forClass(CompleteObjectUploadCommand.class);
        verify(uploadService).completeInternalUpload(eq(SERVICE_SUBJECT), command.capture());
        assertThat(command.getValue().sessionId()).isEqualTo(sessionId);
        assertThat(command.getValue().objectId()).isEqualTo(objectId);
        assertThat(command.getValue().versionId()).isEqualTo(versionId);
        assertThat(command.getValue().content().checksumSha256()).isEqualTo("sha256-note");
        assertThat(command.getValue().content().contentType()).isEqualTo("text/plain");
        assertThat(command.getValue().content().contentLength()).isEqualTo(content.length);
        assertThat(command.getValue().content().openStream().readAllBytes()).isEqualTo(content);
    }

    @Test
    void getMetadataShouldUseServiceSubject() throws Exception {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        ObjectQueryApplicationService queryService = mock(ObjectQueryApplicationService.class);
        when(queryService.getInternalMetadata(objectId, SERVICE_SUBJECT)).thenReturn(new ObjectMetadataResult(
                objectId,
                versionId,
                "DRIVE_FILE",
                SERVICE_SUBJECT,
                "drive",
                "DRIVE_UPLOAD",
                "upload-7",
                "PRIVATE",
                "ACTIVE",
                "note.txt",
                "text/plain",
                4,
                "sha256-note",
                ""
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(
                mock(ObjectUploadApplicationService.class),
                queryService,
                mock(ObjectAccessApplicationService.class),
                mock(ObjectReferenceApplicationService.class),
                mock(ObjectLifecycleApplicationService.class))).build();

        mvc.perform(get("/internal/oss/objects/{objectId}", objectId)
                        .principal(serviceAuthentication(SERVICE_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value(objectId.toString()));

        verify(queryService).getInternalMetadata(objectId, SERVICE_SUBJECT);
    }

    @Test
    void createSignedUrlShouldUseServiceSubjectAndKeepActorNull() throws Exception {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        ObjectAccessApplicationService accessService = mock(ObjectAccessApplicationService.class);
        when(accessService.createInternalSignedDownloadUrl(any(), eq(SERVICE_SUBJECT))).thenReturn(
                new ObjectSignedUrlResult(
                        "http://localhost:12880/files/signed",
                        "GET",
                        Instant.parse("2026-05-07T00:05:00Z"),
                        "private, max-age=300"
                ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(
                mock(ObjectUploadApplicationService.class),
                mock(ObjectQueryApplicationService.class),
                accessService,
                mock(ObjectReferenceApplicationService.class),
                mock(ObjectLifecycleApplicationService.class))).build();

        mvc.perform(get("/internal/oss/objects/{objectId}/signed-url", objectId)
                        .param("versionId", versionId.toString())
                        .principal(serviceAuthentication(SERVICE_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://localhost:12880/files/signed"));

        ArgumentCaptor<CreateSignedUrlCommand> command = ArgumentCaptor.forClass(CreateSignedUrlCommand.class);
        verify(accessService).createInternalSignedDownloadUrl(command.capture(), eq(SERVICE_SUBJECT));
        assertThat(command.getValue().objectId()).isEqualTo(objectId);
        assertThat(command.getValue().versionId()).isEqualTo(versionId);
        assertThat(command.getValue().ttlSeconds()).isEqualTo(300);
        assertThat(command.getValue().actorId()).isNull();
    }

    @Test
    void deleteObjectShouldKeepBusinessActorSeparateFromServiceSubject() throws Exception {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        ObjectLifecycleApplicationService lifecycleService = mock(ObjectLifecycleApplicationService.class);
        when(lifecycleService.deleteInternalObject(any(), eq(SERVICE_SUBJECT))).thenReturn(
                new ObjectLifecycleResult(
                        objectId,
                        versionId,
                        "PURGED",
                        false,
                        true,
                        "object purged",
                        Instant.parse("2026-05-07T00:10:00Z")
                ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(
                mock(ObjectUploadApplicationService.class),
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                mock(ObjectReferenceApplicationService.class),
                lifecycleService)).build();

        mvc.perform(delete("/internal/oss/objects/{objectId}", objectId)
                        .param("actorId", "user-7")
                        .principal(serviceAuthentication(SERVICE_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value(objectId.toString()));

        ArgumentCaptor<DeleteObjectCommand> command = ArgumentCaptor.forClass(DeleteObjectCommand.class);
        verify(lifecycleService).deleteInternalObject(command.capture(), eq(SERVICE_SUBJECT));
        assertThat(command.getValue().objectId()).isEqualTo(objectId);
        assertThat(command.getValue().actorId()).isEqualTo("user-7");
    }

    @Test
    void getMetadataShouldRejectNullAuthenticationWithoutApplicationInteraction() {
        assertRejectedServiceAuthentication(null);
    }

    @Test
    void getMetadataShouldRejectAuthenticatedBlankSubjectWithoutApplicationInteraction() {
        assertRejectedServiceAuthentication(new UsernamePasswordAuthenticationToken("   ", "n/a", List.of()));
    }

    @Test
    void getMetadataShouldRejectUnauthenticatedSubjectWithoutApplicationInteraction() {
        assertRejectedServiceAuthentication(new UsernamePasswordAuthenticationToken(SERVICE_SUBJECT, "n/a"));
    }

    @Test
    void getMetadataShouldTrimAuthenticatedServiceSubject() {
        UUID objectId = uuid(1);
        ObjectQueryApplicationService queryService = mock(ObjectQueryApplicationService.class);
        InternalOssObjectController controller = controller(
                mock(ObjectUploadApplicationService.class),
                queryService,
                mock(ObjectAccessApplicationService.class),
                mock(ObjectReferenceApplicationService.class),
                mock(ObjectLifecycleApplicationService.class));

        controller.getMetadata(
                objectId,
                new UsernamePasswordAuthenticationToken("  " + SERVICE_SUBJECT + "  ", "n/a", List.of()));

        verify(queryService).getInternalMetadata(objectId, SERVICE_SUBJECT);
    }

    @Test
    void bindAndReleaseReferenceShouldReturnReferenceState() throws Exception {
        UUID objectId = uuid(1);
        UUID referenceId = uuid(3);
        ObjectReferenceApplicationService referenceService = mock(ObjectReferenceApplicationService.class);
        when(referenceService.bindInternalReference(eq(SERVICE_SUBJECT), any())).thenReturn(new ObjectReferenceResult(
                referenceId,
                objectId,
                uuid(2),
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                "ACTIVE",
                null,
                Instant.parse("2026-05-07T00:00:00Z"),
                null
        ));
        when(referenceService.releaseInternalReference(eq(SERVICE_SUBJECT), any())).thenReturn(new ObjectReferenceResult(
                referenceId,
                objectId,
                uuid(2),
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                "RELEASED",
                null,
                Instant.parse("2026-05-07T00:00:00Z"),
                Instant.parse("2026-05-07T00:05:00Z")
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(referenceService)).build();

        mvc.perform(post("/internal/oss/objects/{objectId}/references", objectId)
                        .principal(serviceAuthentication(SERVICE_SUBJECT))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionId": "%s",
                                  "referenceId": "%s",
                                  "subjectService": "community-app",
                                  "subjectDomain": "user",
                                  "subjectType": "avatar",
                                  "subjectId": "7",
                                  "referenceRole": "PRIMARY",
                                  "retainUntil": "2026-05-07T01:00:00Z",
                                  "actorId": "7"
                                }
                                """.formatted(uuid(2), referenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.referenceRole").value("PRIMARY"));

        ArgumentCaptor<BindObjectReferenceCommand> command = ArgumentCaptor.forClass(BindObjectReferenceCommand.class);
        verify(referenceService).bindInternalReference(eq(SERVICE_SUBJECT), command.capture());
        assertThat(command.getValue().referenceId()).isEqualTo(referenceId);

        mvc.perform(delete("/internal/oss/objects/{objectId}/references/{referenceId}", objectId, referenceId)
                        .principal(serviceAuthentication(SERVICE_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        verify(referenceService).releaseInternalReference(eq(SERVICE_SUBJECT), any());
    }

    @Test
    void getReferenceShouldUseServiceSubject() throws Exception {
        UUID objectId = uuid(1);
        UUID referenceId = uuid(3);
        ObjectReferenceApplicationService referenceService = mock(ObjectReferenceApplicationService.class);
        when(referenceService.getInternalReference(objectId, referenceId, SERVICE_SUBJECT)).thenReturn(
                new ObjectReferenceResult(
                        referenceId,
                        objectId,
                        uuid(2),
                        SERVICE_SUBJECT,
                        "drive",
                        "attachment",
                        "file-7",
                        "PRIMARY",
                        "ACTIVE",
                        null,
                        Instant.parse("2026-05-07T00:00:00Z"),
                        null
                ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(referenceService)).build();

        mvc.perform(get("/internal/oss/objects/{objectId}/references/{referenceId}", objectId, referenceId)
                        .principal(serviceAuthentication(SERVICE_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value(referenceId.toString()));

        verify(referenceService).getInternalReference(objectId, referenceId, SERVICE_SUBJECT);
    }

    @Test
    void semanticReferenceConflictShouldBeStableHttp409() throws Exception {
        UUID objectId = uuid(1);
        ObjectReferenceApplicationService referenceService = mock(ObjectReferenceApplicationService.class);
        when(referenceService.bindInternalReference(eq(SERVICE_SUBJECT), any())).thenThrow(new BusinessException(
                new SimpleErrorCode(40901, "object reference semantic conflict", ErrorKind.CONFLICT)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(referenceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/internal/oss/objects/{objectId}/references", objectId)
                        .principal(serviceAuthentication(SERVICE_SUBJECT))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "referenceId": "00000000-0000-7000-8000-000000000005",
                                  "subjectService": "community-app",
                                  "subjectDomain": "user",
                                  "subjectType": "avatar",
                                  "subjectId": "7",
                                  "referenceRole": "PRIMARY",
                                  "actorId": "7"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.httpStatus").value(409));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static InternalOssObjectController controller(ObjectReferenceApplicationService referenceService) {
        return controller(
                mock(ObjectUploadApplicationService.class),
                mock(ObjectQueryApplicationService.class),
                mock(ObjectAccessApplicationService.class),
                referenceService,
                mock(ObjectLifecycleApplicationService.class));
    }

    private static InternalOssObjectController controller(
            ObjectUploadApplicationService uploadService,
            ObjectQueryApplicationService queryService,
            ObjectAccessApplicationService accessService,
            ObjectReferenceApplicationService referenceService,
            ObjectLifecycleApplicationService lifecycleService
    ) {
        return new InternalOssObjectController(
                uploadService, queryService, accessService, referenceService, lifecycleService);
    }

    private static UsernamePasswordAuthenticationToken serviceAuthentication(String subject) {
        return new UsernamePasswordAuthenticationToken(subject, "n/a", List.of());
    }

    private static void assertRejectedServiceAuthentication(Authentication authentication) {
        UUID objectId = uuid(1);
        ObjectQueryApplicationService queryService = mock(ObjectQueryApplicationService.class);
        InternalOssObjectController controller = controller(
                mock(ObjectUploadApplicationService.class),
                queryService,
                mock(ObjectAccessApplicationService.class),
                mock(ObjectReferenceApplicationService.class),
                mock(ObjectLifecycleApplicationService.class));

        assertThatThrownBy(() -> controller.getMetadata(objectId, authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authenticated service subject must not be blank");
        verifyNoInteractions(queryService);
    }

    private static void assertClassExists(String className) {
        assertThat(catchThrowable(() -> Class.forName(className))).isNull();
    }

    private static void assertMethodExists(Class<?> type, String name, Class<?>... parameterTypes) {
        assertThat(catchThrowable(() -> type.getMethod(name, parameterTypes)))
                .as(type.getSimpleName() + "." + name)
                .isNull();
    }
}
