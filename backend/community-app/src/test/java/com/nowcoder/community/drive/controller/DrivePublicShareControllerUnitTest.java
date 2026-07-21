package com.nowcoder.community.drive.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.drive.application.DriveShareApplicationService;
import com.nowcoder.community.drive.application.command.VerifyDriveShareCommand;
import com.nowcoder.community.drive.application.result.DrivePublicShareGateResult;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.result.DriveShareResult;
import com.nowcoder.community.drive.security.DriveSecurityRules;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DrivePublicShareController.class)
@Import({
        DrivePublicShareController.class,
        DriveSecurityRules.class,
        CommunitySecurityConfig.class,
        WebMvcSliceJsonCodecTestConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class DrivePublicShareControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DriveShareApplicationService shareApplicationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void publicShareGateShouldNotExposeEntryMetadataBeforeVerification() throws Exception {
        when(shareApplicationService.loadPublicShareGate("token-a")).thenReturn(new DrivePublicShareGateResult(
                "token-a",
                true
        ));

        mockMvc.perform(get("/api/drive/shares/token-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareToken").value("token-a"))
                .andExpect(jsonPath("$.data.requiresPassword").value(true))
                .andExpect(jsonPath("$.data.shareId").doesNotExist())
                .andExpect(jsonPath("$.data.entryId").doesNotExist())
                .andExpect(jsonPath("$.data.entryName").doesNotExist())
                .andExpect(jsonPath("$.data.entryType").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.ticket").doesNotExist());
    }

    @Test
    void verifyShareShouldNotRequireAuthentication() throws Exception {
        when(shareApplicationService.verifyShare(any())).thenReturn(new DriveShareResult(
                uuid(1),
                uuid(2),
                "token-a",
                "a.txt",
                "FILE",
                Instant.parse("2026-05-10T00:00:00Z"),
                "ACTIVE",
                "ticket-a",
                Instant.parse("2026-05-09T00:15:00Z")
        ));

        mockMvc.perform(post("/api/drive/shares/token-a/verify")
                        .contentType("application/json")
                        .content("{\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticket").value("ticket-a"));
    }

    @Test
    void verifyShareShouldHashVisitorInputsBeforeEnteringApplication() throws Exception {
        when(shareApplicationService.verifyShare(any())).thenReturn(new DriveShareResult(
                uuid(1),
                uuid(2),
                "token-a",
                "a.txt",
                "FILE",
                Instant.parse("2026-05-10T00:00:00Z"),
                "ACTIVE",
                "ticket-a",
                Instant.parse("2026-05-09T00:15:00Z")
        ));
        String remoteAddress = "203.0.113.9";
        String longUserAgent = "agent-".repeat(800);

        verifyShare(remoteAddress, longUserAgent);
        verifyShare(remoteAddress, longUserAgent);
        verifyShare("203.0.113.10", longUserAgent);
        verifyShare(remoteAddress, longUserAgent + "-changed");

        ArgumentCaptor<VerifyDriveShareCommand> commands =
                ArgumentCaptor.forClass(VerifyDriveShareCommand.class);
        verify(shareApplicationService, times(4)).verifyShare(commands.capture());
        List<String> fingerprints = commands.getAllValues().stream()
                .map(VerifyDriveShareCommand::visitorFingerprint)
                .toList();
        assertThat(fingerprints).allMatch(value -> value.matches("[0-9a-f]{64}"));
        assertThat(fingerprints.get(0))
                .doesNotContain(remoteAddress)
                .doesNotContain(longUserAgent)
                .isEqualTo(fingerprints.get(1))
                .isNotEqualTo(fingerprints.get(2))
                .isNotEqualTo(fingerprints.get(3));
    }

    @Test
    void downloadUrlShouldNotRequireAuthentication() throws Exception {
        when(shareApplicationService.createShareDownloadUrl(eq("token-a"), eq("ticket-a"), eq(uuid(3)))).thenReturn(
                new DriveDownloadUrlResult(uuid(3), "https://cdn.example.test/file", Instant.parse("2026-05-09T00:16:00Z"))
        );

        UUID entryId = uuid(3);
        mockMvc.perform(get("/api/drive/shares/token-a/download-url")
                        .param("ticket", "ticket-a")
                        .param("entryId", entryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value(entryId.toString()))
                .andExpect(jsonPath("$.data.url").value("https://cdn.example.test/file"));

        verify(shareApplicationService).createShareDownloadUrl("token-a", "ticket-a", entryId);
    }

    @Test
    void shareEntriesShouldNotRequireAuthentication() throws Exception {
        UUID parentId = uuid(4);
        UUID entryId = uuid(5);
        when(shareApplicationService.listShareEntries(eq("token-a"), eq("ticket-a"), eq(parentId))).thenReturn(List.of(
                new DriveEntryResult(entryId, parentId, "FILE", "child.txt", 8, "text/plain", "ACTIVE", Instant.parse("2026-05-09T00:00:00Z"))
        ));

        mockMvc.perform(get("/api/drive/shares/token-a/entries")
                        .param("ticket", "ticket-a")
                        .param("parentId", parentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].entryId").value(entryId.toString()))
                .andExpect(jsonPath("$.data[0].name").value("child.txt"));

        verify(shareApplicationService).listShareEntries("token-a", "ticket-a", parentId);
    }

    private void verifyShare(String remoteAddress, String userAgent) throws Exception {
        mockMvc.perform(post("/api/drive/shares/token-a/verify")
                        .with(request -> {
                            request.setRemoteAddr(remoteAddress);
                            return request;
                        })
                        .header("User-Agent", userAgent)
                        .contentType("application/json")
                        .content("{\"password\":\"1234\"}"))
                .andExpect(status().isOk());
    }
}
