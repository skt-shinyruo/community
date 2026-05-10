package com.nowcoder.community.drive.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.drive.application.DriveEntryApplicationService;
import com.nowcoder.community.drive.application.DriveShareApplicationService;
import com.nowcoder.community.drive.application.DriveSpaceApplicationService;
import com.nowcoder.community.drive.application.DriveTrashApplicationService;
import com.nowcoder.community.drive.application.DriveUploadApplicationService;
import com.nowcoder.community.drive.application.command.CompleteDriveUploadCommand;
import com.nowcoder.community.drive.application.command.PrepareDriveUploadCommand;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.result.DriveShareResult;
import com.nowcoder.community.drive.application.result.DriveUploadSessionResult;
import com.nowcoder.community.drive.security.DriveSecurityRules;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.ArgumentMatchers.any;
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
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class DriveControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DriveSpaceApplicationService spaceApplicationService;

    @MockBean
    private DriveEntryApplicationService entryApplicationService;

    @MockBean
    private DriveUploadApplicationService uploadApplicationService;

    @MockBean
    private DriveTrashApplicationService trashApplicationService;

    @MockBean
    private DriveShareApplicationService shareApplicationService;

    @MockBean
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
    void prepareUploadShouldReturnProviderFreeUploadInstruction() throws Exception {
        UUID userId = uuid(7);
        UUID uploadId = uuid(20);
        String fileKey = "drive/" + uploadId + "/report.pdf";
        when(uploadApplicationService.prepareUpload(any())).thenReturn(new DriveUploadSessionResult(
                uploadId.toString(),
                fileKey,
                new DriveUploadSessionResult.UploadInstruction(
                        "/api/drive/uploads/" + uploadId + "/complete",
                        "POST",
                        "file",
                        Map.of("fileKey", fileKey),
                        Map.of()
                ),
                new DriveUploadSessionResult.UploadConstraints(10_737_418_240L, List.of()),
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
                .andExpect(jsonPath("$.data.fileKey").value(fileKey))
                .andExpect(jsonPath("$.data.upload.url").value("/api/drive/uploads/" + uploadId + "/complete"))
                .andExpect(jsonPath("$.data.upload.fileField").value("file"))
                .andExpect(jsonPath("$.data.upload.fields.fileKey").value(fileKey));

        ArgumentCaptor<PrepareDriveUploadCommand> commandCaptor = ArgumentCaptor.forClass(PrepareDriveUploadCommand.class);
        verify(uploadApplicationService).prepareUpload(commandCaptor.capture());
        assertThat(commandCaptor.getValue().actorUserId()).isEqualTo(userId);
        assertThat(commandCaptor.getValue().parentId()).isNull();
        assertThat(commandCaptor.getValue().fileName()).isEqualTo("report.pdf");
        assertThat(commandCaptor.getValue().contentLength()).isEqualTo(1024L);
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

        ArgumentCaptor<CompleteDriveUploadCommand> commandCaptor = ArgumentCaptor.forClass(CompleteDriveUploadCommand.class);
        verify(uploadApplicationService).completeUpload(commandCaptor.capture());
        assertThat(commandCaptor.getValue().actorUserId()).isEqualTo(userId);
        assertThat(commandCaptor.getValue().uploadId()).isEqualTo(uploadId);
        assertThat(commandCaptor.getValue().content().contentLength()).isEqualTo(3L);
    }

    @Test
    void createShareShouldReturnSharePayload() throws Exception {
        UUID userId = uuid(7);
        UUID entryId = uuid(30);
        UUID shareId = uuid(40);
        when(shareApplicationService.createShare(any())).thenReturn(new DriveShareResult(
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
                .andExpect(jsonPath("$.data.shareId").value(shareId.toString()))
                .andExpect(jsonPath("$.data.shareToken").value("token-a"))
                .andExpect(jsonPath("$.data.entryName").value("report.pdf"));
    }
}
