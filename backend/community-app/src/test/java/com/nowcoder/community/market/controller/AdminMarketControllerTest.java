package com.nowcoder.community.market.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.market.application.result.MarketDisputeResult;
import com.nowcoder.community.market.security.MarketSecurityRules;
import com.nowcoder.community.market.application.AdminMarketApplicationService;
import com.nowcoder.community.market.application.MarketDisputeApplicationService;
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
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
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
        AdminMarketApplicationService.class,
        MarketSecurityRules.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AdminMarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDisputeApplicationService marketDisputeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        UUID userId = uuid(2);
        mockMvc.perform(post("/api/admin/market/disputes/00000000-0000-7000-8000-000000000001/resolve-refund")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())).authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("{\"note\":\"refund\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminResolutionApisShouldDelegateToService() throws Exception {
        UUID disputeId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000011");
        UUID buyerUserId = uuid(9);
        UUID sellerUserId = uuid(7);
        UUID adminUserId = uuid(99);
        MarketDisputeResult dispute = new MarketDisputeResult(
                disputeId,
                orderId,
                "PHYSICAL",
                buyerUserId,
                sellerUserId,
                "ADMIN_RESOLVED",
                "货不对板",
                "和描述不一致",
                "管理员处理",
                "REFUND",
                adminUserId,
                new Date(),
                new Date(),
                new Date()
        );
        when(marketDisputeService.listOpenDisputes()).thenReturn(List.of(dispute));
        when(marketDisputeService.adminResolveRefund(disputeId, adminUserId, "refund")).thenReturn(dispute);
        when(marketDisputeService.adminResolveRelease(disputeId, adminUserId, "release")).thenReturn(new MarketDisputeResult(
                disputeId,
                orderId,
                "PHYSICAL",
                buyerUserId,
                sellerUserId,
                "ADMIN_RESOLVED",
                "货不对板",
                "和描述不一致",
                "管理员处理",
                "RELEASE",
                adminUserId,
                new Date(),
                new Date(),
                new Date()
        ));

        mockMvc.perform(get("/api/admin/market/disputes")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].disputeId").value(disputeId.toString()));

        mockMvc.perform(post("/api/admin/market/disputes/" + disputeId + "/resolve-refund")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"note\":\"refund\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolutionType").value("REFUND"));

        mockMvc.perform(post("/api/admin/market/disputes/" + disputeId + "/resolve-release")
                        .with(jwt().jwt(jwt -> jwt.subject(adminUserId.toString())).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"note\":\"release\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolutionType").value("RELEASE"));

        verify(marketDisputeService).adminResolveRefund(disputeId, adminUserId, "refund");
        verify(marketDisputeService).adminResolveRelease(disputeId, adminUserId, "release");
    }
}
