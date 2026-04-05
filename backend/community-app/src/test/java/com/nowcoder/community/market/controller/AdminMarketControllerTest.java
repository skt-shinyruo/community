package com.nowcoder.community.market.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.market.dto.MarketDisputeResponse;
import com.nowcoder.community.market.security.MarketSecurityRules;
import com.nowcoder.community.market.service.MarketDisputeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMarketController.class)
@Import({
        AdminMarketController.class,
        MarketSecurityRules.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AdminMarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDisputeService marketDisputeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/admin/market/disputes/1/resolve-refund")
                        .with(jwt().jwt(jwt -> jwt.subject("2")).authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("{\"note\":\"refund\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminResolutionApisShouldDelegateToService() throws Exception {
        MarketDisputeResponse dispute = new MarketDisputeResponse(
                1L,
                11L,
                "PHYSICAL",
                9,
                7,
                "ADMIN_RESOLVED",
                "货不对板",
                "和描述不一致",
                "管理员处理",
                "REFUND",
                99,
                new Date(),
                new Date(),
                new Date()
        );
        when(marketDisputeService.listOpenDisputes()).thenReturn(List.of(dispute));
        when(marketDisputeService.adminResolveRefund(1L, 99, "refund")).thenReturn(dispute);
        when(marketDisputeService.adminResolveRelease(1L, 99, "release")).thenReturn(new MarketDisputeResponse(
                1L,
                11L,
                "PHYSICAL",
                9,
                7,
                "ADMIN_RESOLVED",
                "货不对板",
                "和描述不一致",
                "管理员处理",
                "RELEASE",
                99,
                new Date(),
                new Date(),
                new Date()
        ));

        mockMvc.perform(get("/api/admin/market/disputes")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].disputeId").value(1));

        mockMvc.perform(post("/api/admin/market/disputes/1/resolve-refund")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"note\":\"refund\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolutionType").value("REFUND"));

        mockMvc.perform(post("/api/admin/market/disputes/1/resolve-release")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"note\":\"release\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolutionType").value("RELEASE"));

        verify(marketDisputeService).adminResolveRefund(1L, 99, "refund");
        verify(marketDisputeService).adminResolveRelease(1L, 99, "release");
    }
}
