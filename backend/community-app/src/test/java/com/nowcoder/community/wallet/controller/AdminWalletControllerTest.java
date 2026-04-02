package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.wallet.security.WalletSecurityRules;
import com.nowcoder.community.wallet.service.AdminWalletOpsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminWalletController.class)
@Import({
        AdminWalletController.class,
        WalletSecurityRules.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AdminWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminWalletOpsService adminWalletOpsService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/wallet/admin/freeze")
                        .with(jwt().jwt(jwt -> jwt.subject("2")).authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": 101,
                                  "reason": "risk review"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminFreezeShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/api/wallet/admin/freeze")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": 101,
                                  "reason": "risk review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(adminWalletOpsService).freezeWallet(99, 101, "risk review");
    }

    @Test
    void adminReverseShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/api/wallet/admin/reverse")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "txnRef": "transfer:req-1",
                                  "reason": "fraud report"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(adminWalletOpsService).reverseTxn(99, "transfer:req-1", "fraud report");
    }
}
