package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.wallet.application.AdminWalletApplicationService;
import com.nowcoder.community.wallet.application.WalletAdminOpsApplicationService;
import com.nowcoder.community.wallet.security.WalletSecurityRules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminWalletController.class)
@Import({
        AdminWalletController.class,
        AdminWalletApplicationService.class,
        WalletSecurityRules.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AdminWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletAdminOpsApplicationService adminWalletOpsService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        UUID actorUserId = uuid(2);
        UUID targetUserId = uuid(101);
        mockMvc.perform(post("/api/wallet/admin/freeze")
                        .with(jwt().jwt(jwt -> jwt.subject(actorUserId.toString())).authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": "%s",
                                  "reason": "risk review"
                                }
                                """.formatted(targetUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/wallet/admin/reverse")
                        .with(jwt().jwt(jwt -> jwt.subject(actorUserId.toString())).authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "txnRef": "transfer:req-1",
                                  "reason": "fraud report"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminFreezeShouldDelegateToService() throws Exception {
        UUID actorUserId = uuid(99);
        UUID targetUserId = uuid(101);
        mockMvc.perform(post("/api/wallet/admin/freeze")
                        .with(jwt().jwt(jwt -> jwt.subject(actorUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": "%s",
                                  "reason": "risk review"
                                }
                                """.formatted(targetUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(adminWalletOpsService).freezeWallet(actorUserId, targetUserId, "risk review");
    }

    @Test
    void adminReverseShouldDelegateToService() throws Exception {
        UUID actorUserId = uuid(99);
        mockMvc.perform(post("/api/wallet/admin/reverse")
                        .with(jwt().jwt(jwt -> jwt.subject(actorUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "txnRef": "transfer:req-1",
                                  "reason": "fraud report"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(adminWalletOpsService).reverseTxn(actorUserId, "transfer:req-1", "fraud report");
    }
}
