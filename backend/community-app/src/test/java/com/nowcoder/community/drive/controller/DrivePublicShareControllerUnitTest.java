package com.nowcoder.community.drive.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.drive.application.DriveShareApplicationService;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveShareResult;
import com.nowcoder.community.drive.security.DriveSecurityRules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void publicShareMetadataShouldNotRequireAuthentication() throws Exception {
        when(shareApplicationService.loadPublicShare("token-a")).thenReturn(new DriveShareResult(
                uuid(1),
                uuid(2),
                "token-a",
                "a.txt",
                "FILE",
                Instant.parse("2026-05-10T00:00:00Z"),
                "ACTIVE",
                null,
                null
        ));

        mockMvc.perform(get("/api/drive/shares/token-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareToken").value("token-a"))
                .andExpect(jsonPath("$.data.entryName").value("a.txt"));
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
}
