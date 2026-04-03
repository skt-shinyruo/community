package com.nowcoder.community.market.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.market.dto.VirtualDisputeResponse;
import com.nowcoder.community.market.security.MarketSecurityRules;
import com.nowcoder.community.market.service.VirtualDisputeService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminVirtualMarketController.class)
@Import({
        AdminVirtualMarketController.class,
        MarketSecurityRules.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AdminVirtualMarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VirtualDisputeService virtualDisputeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/admin/market/virtual/disputes/1/resolve-refund")
                        .with(jwt().jwt(jwt -> jwt.subject("2")).authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("{\"note\":\"refund\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminResolutionApisShouldDelegateToService() throws Exception {
        VirtualDisputeResponse dispute = new VirtualDisputeResponse(
                1L,
                11L,
                9,
                7,
                "ADMIN_RESOLVED",
                "商品无效",
                "兑换失败",
                "管理员处理",
                "REFUND",
                99,
                new Date(),
                new Date(),
                new Date()
        );
        when(virtualDisputeService.listOpenDisputes()).thenReturn(List.of(dispute));
        when(virtualDisputeService.adminResolveRefund(1L, 99, "refund")).thenReturn(dispute);
        when(virtualDisputeService.adminResolveRelease(1L, 99, "release")).thenReturn(new VirtualDisputeResponse(
                1L,
                11L,
                9,
                7,
                "ADMIN_RESOLVED",
                "商品无效",
                "兑换失败",
                "管理员处理",
                "RELEASE",
                99,
                new Date(),
                new Date(),
                new Date()
        ));

        mockMvc.perform(get("/api/admin/market/virtual/disputes")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].disputeId").value(1));

        mockMvc.perform(post("/api/admin/market/virtual/disputes/1/resolve-refund")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"note\":\"refund\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.resolutionType").value("REFUND"));

        mockMvc.perform(post("/api/admin/market/virtual/disputes/1/resolve-release")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"note\":\"release\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.resolutionType").value("RELEASE"));

        verify(virtualDisputeService).adminResolveRefund(1L, 99, "refund");
        verify(virtualDisputeService).adminResolveRelease(1L, 99, "release");
    }
}
