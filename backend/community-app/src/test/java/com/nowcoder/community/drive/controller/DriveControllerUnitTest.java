package com.nowcoder.community.drive.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.drive.application.DriveEntryApplicationService;
import com.nowcoder.community.drive.application.DriveShareApplicationService;
import com.nowcoder.community.drive.application.DriveSpaceApplicationService;
import com.nowcoder.community.drive.application.DriveTrashApplicationService;
import com.nowcoder.community.drive.application.DriveUploadApplicationService;
import com.nowcoder.community.drive.application.DriveUploadApplicationService.CompleteUploadCommand;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.DriveUploadApplicationService.PrepareUploadCommand;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.DriveShareApplicationService.ShareResult;
import com.nowcoder.community.drive.application.DriveShareApplicationService.SharePageResult;
import com.nowcoder.community.drive.application.DriveUploadApplicationService.UploadSessionResult;
import com.nowcoder.community.drive.security.DriveSecurityRules;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriveController.class)
@Import({
        DriveController.class,
        DriveSecurityRules.class,
        CommunitySecurityConfig.class,
        WebMvcSliceJsonCodecTestConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class DriveControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriveSpaceApplicationService spaceApplicationService;

    @MockitoBean
    private DriveEntryApplicationService entryApplicationService;

    @MockitoBean
    private DriveUploadApplicationService uploadApplicationService;

    @MockitoBean
    private DriveTrashApplicationService trashApplicationService;

    @MockitoBean
    private DriveShareApplicationService shareApplicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void privateDriveApisShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/drive/space"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void coreReadModelsShouldKeepExactJsonFields() throws Exception {
        UUID userId = uuid(7);
        UUID spaceId = uuid(8);
        UUID parentId = uuid(9);
        UUID entryId = uuid(10);
        Instant updatedAt = Instant.parse("2026-05-09T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-05-09T00:10:00Z");
        when(spaceApplicationService.getSpace(userId)).thenReturn(new DriveSpaceApplicationService.DriveSpaceResult(
                spaceId,
                userId,
                1_000L,
                200L,
                800L
        ));
        when(entryApplicationService.listEntries(userId, null)).thenReturn(List.of(new DriveEntryResult(
                entryId,
                parentId,
                "FILE",
                "report.pdf",
                200L,
                "application/pdf",
                "ACTIVE",
                updatedAt
        )));
        when(entryApplicationService.createDownloadUrl(userId, entryId)).thenReturn(new DriveDownloadUrlResult(
                entryId,
                "https://cdn.example.test/report.pdf",
                expiresAt
        ));

        mockMvc.perform(get("/api/drive/space")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", aMapWithSize(5)))
                .andExpect(jsonPath("$.data.spaceId").value(spaceId.toString()))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.quotaBytes").value(1_000L))
                .andExpect(jsonPath("$.data.usedBytes").value(200L))
                .andExpect(jsonPath("$.data.remainingBytes").value(800L));

        mockMvc.perform(get("/api/drive/entries")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]", aMapWithSize(8)))
                .andExpect(jsonPath("$.data[0].entryId").value(entryId.toString()))
                .andExpect(jsonPath("$.data[0].parentId").value(parentId.toString()))
                .andExpect(jsonPath("$.data[0].type").value("FILE"))
                .andExpect(jsonPath("$.data[0].name").value("report.pdf"))
                .andExpect(jsonPath("$.data[0].sizeBytes").value(200L))
                .andExpect(jsonPath("$.data[0].mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].updatedAt").value(updatedAt.toString()));

        mockMvc.perform(get("/api/drive/entries/{entryId}/download-url", entryId)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", aMapWithSize(3)))
                .andExpect(jsonPath("$.data.entryId").value(entryId.toString()))
                .andExpect(jsonPath("$.data.url").value("https://cdn.example.test/report.pdf"))
                .andExpect(jsonPath("$.data.expiresAt").value(expiresAt.toString()));
    }

    @Test
    void prepareUploadShouldReturnProviderFreeUploadInstruction() throws Exception {
        UUID userId = uuid(7);
        UUID uploadId = uuid(20);
        String fileKey = "drive/" + uploadId + "/report.pdf";
        when(uploadApplicationService.prepareUpload(any())).thenReturn(new UploadSessionResult(
                uploadId.toString(),
                fileKey,
                new UploadSessionResult.UploadInstruction(
                        "/api/drive/uploads/" + uploadId + "/complete",
                        "POST",
                        "file",
                        Map.of("fileKey", fileKey),
                        Map.of()
                ),
                new UploadSessionResult.UploadConstraints(10_737_418_240L, List.of()),
                Instant.parse("2026-05-09T00:15:00Z")
        ));

        mockMvc.perform(post("/api/drive/uploads")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":"","fileName":"report.pdf","contentType":"application/pdf","contentLength":1024,"checksumSha256":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", aMapWithSize(5)))
                .andExpect(jsonPath("$.data.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.data.fileKey").value(fileKey))
                .andExpect(jsonPath("$.data.upload", aMapWithSize(5)))
                .andExpect(jsonPath("$.data.upload.url").value("/api/drive/uploads/" + uploadId + "/complete"))
                .andExpect(jsonPath("$.data.upload.method").value("POST"))
                .andExpect(jsonPath("$.data.upload.fileField").value("file"))
                .andExpect(jsonPath("$.data.upload.fields", aMapWithSize(1)))
                .andExpect(jsonPath("$.data.upload.fields.fileKey").value(fileKey))
                .andExpect(jsonPath("$.data.upload.headers", aMapWithSize(0)))
                .andExpect(jsonPath("$.data.constraints", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.constraints.maxBytes").value(10_737_418_240L))
                .andExpect(jsonPath("$.data.constraints.mimeTypes").isEmpty())
                .andExpect(jsonPath("$.data.expiresAt").value("2026-05-09T00:15:00Z"));

        ArgumentCaptor<PrepareUploadCommand> commandCaptor = ArgumentCaptor.forClass(PrepareUploadCommand.class);
        verify(uploadApplicationService).prepareUpload(commandCaptor.capture());
        assertThat(commandCaptor.getValue().actorUserId()).isEqualTo(userId);
        assertThat(commandCaptor.getValue().parentId()).isNull();
        assertThat(commandCaptor.getValue().fileName()).isEqualTo("report.pdf");
        assertThat(commandCaptor.getValue().contentLength()).isEqualTo(1024L);
    }

    @Test
    void prepareUploadRequestShouldIgnoreUnknownFields() throws Exception {
        UUID userId = uuid(7);

        mockMvc.perform(post("/api/drive/uploads")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"report.pdf","contentLength":1024,"unknown":true}
                                """))
                .andExpect(status().isOk());

        verify(uploadApplicationService).prepareUpload(any());
    }

    @Test
    void prepareUploadRequestShouldRejectInvalidValuesBeforeApplication() throws Exception {
        UUID userId = uuid(7);

        mockMvc.perform(post("/api/drive/uploads")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":" ","contentLength":-1}
                                """))
                .andExpect(status().isBadRequest());

        verify(uploadApplicationService, never()).prepareUpload(any());
    }

    @Test
    void completeUploadShouldAdaptMultipartFile() throws Exception {
        UUID userId = uuid(7);
        UUID uploadId = uuid(20);
        UUID entryId = uuid(30);
        when(uploadApplicationService.completeUpload(any())).thenReturn(new DriveEntryResult(
                entryId,
                null,
                "FILE",
                "report.pdf",
                3L,
                "application/pdf",
                "ACTIVE",
                Instant.parse("2026-05-09T00:16:00Z")
        ));
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "abc".getBytes());

        mockMvc.perform(multipart("/api/drive/uploads/{uploadId}/complete", uploadId)
                        .file(file)
                        .param("fileKey", "drive/" + uploadId + "/report.pdf")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value(entryId.toString()))
                .andExpect(jsonPath("$.data.name").value("report.pdf"));

        ArgumentCaptor<CompleteUploadCommand> commandCaptor = ArgumentCaptor.forClass(CompleteUploadCommand.class);
        verify(uploadApplicationService).completeUpload(commandCaptor.capture());
        assertThat(commandCaptor.getValue().actorUserId()).isEqualTo(userId);
        assertThat(commandCaptor.getValue().uploadId()).isEqualTo(uploadId);
        assertThat(commandCaptor.getValue().content().contentLength()).isEqualTo(3L);
        assertThat(DriveUploadContent.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("uploadStream", "contentType", "contentLength");
    }

    @Test
    void createShareShouldReturnSharePayload() throws Exception {
        UUID userId = uuid(7);
        UUID entryId = uuid(30);
        UUID shareId = uuid(40);
        when(shareApplicationService.createShare(any())).thenReturn(new ShareResult(
                shareId,
                entryId,
                "token-a",
                "report.pdf",
                "FILE",
                Instant.parse("2026-05-10T00:00:00Z"),
                "ACTIVE",
                null,
                null
        ));

        mockMvc.perform(post("/api/drive/entries/{entryId}/shares", entryId)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"1234","expiresAt":"2026-05-10T00:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", aMapWithSize(9)))
                .andExpect(jsonPath("$.data.shareId").value(shareId.toString()))
                .andExpect(jsonPath("$.data.entryId").value(entryId.toString()))
                .andExpect(jsonPath("$.data.shareToken").value("token-a"))
                .andExpect(jsonPath("$.data.entryName").value("report.pdf"))
                .andExpect(jsonPath("$.data.entryType").value("FILE"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-05-10T00:00:00Z"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.ticket").value(nullValue()))
                .andExpect(jsonPath("$.data.ticketExpiresAt").value(nullValue()));
    }

    @Test
    void listOwnSharesShouldReturnPageMetadata() throws Exception {
        UUID userId = uuid(7);
        UUID entryId = uuid(30);
        UUID shareId = uuid(40);
        when(shareApplicationService.listOwnShares(userId, 0, 20)).thenReturn(new SharePageResult(
                List.of(new ShareResult(
                        shareId,
                        entryId,
                        "token-a",
                        "report.pdf",
                        "FILE",
                        Instant.parse("2026-05-10T00:00:00Z"),
                        "ACTIVE",
                        null,
                        null
                )),
                false,
                0,
                20
        ));

        mockMvc.perform(get("/api/drive/shares")
                        .param("page", "0")
                        .param("size", "20")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", aMapWithSize(4)))
                .andExpect(jsonPath("$.data.items[0]", aMapWithSize(9)))
                .andExpect(jsonPath("$.data.items[0].shareId").value(shareId.toString()))
                .andExpect(jsonPath("$.data.items[0].entryId").value(entryId.toString()))
                .andExpect(jsonPath("$.data.items[0].shareToken").value("token-a"))
                .andExpect(jsonPath("$.data.items[0].entryName").value("report.pdf"))
                .andExpect(jsonPath("$.data.items[0].entryType").value("FILE"))
                .andExpect(jsonPath("$.data.items[0].expiresAt").value("2026-05-10T00:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].ticket").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].ticketExpiresAt").value(nullValue()))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }
}
