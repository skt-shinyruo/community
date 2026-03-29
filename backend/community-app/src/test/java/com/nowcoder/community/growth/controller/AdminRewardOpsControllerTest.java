package com.nowcoder.community.growth.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.growth.dto.AdminGrowthMetricsResponse;
import com.nowcoder.community.growth.dto.AdminRewardItemResponse;
import com.nowcoder.community.growth.dto.AdminRewardItemUpsertRequest;
import com.nowcoder.community.growth.dto.AdminRewardOrderResponse;
import com.nowcoder.community.growth.dto.AdminRewardOrderActionRequest;
import com.nowcoder.community.growth.service.AdminRewardOpsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRewardOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminRewardOpsService adminRewardOpsService;

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/growth/admin/rewards/items")
                        .with(jwt().jwt(jwt -> jwt.subject("2")).authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminItemAndOrderEndpointsShouldReturnOperatorData() throws Exception {
        AdminRewardItemResponse item = new AdminRewardItemResponse();
        item.setId(11L);
        item.setItemName("社群资格");
        item.setStatus("ACTIVE");
        item.setCostBalance(15);
        item.setStock(5);
        item.setPerUserLimit(1);
        item.setFulfillmentMode("MANUAL");

        AdminRewardOrderResponse order = new AdminRewardOrderResponse();
        order.setId(101L);
        order.setItemId(11L);
        order.setStatus("PENDING");
        order.setItemNameSnapshot("社群资格");
        order.setCostBalanceSnapshot(15);
        order.setFulfillmentModeSnapshot("MANUAL");

        AdminGrowthMetricsResponse metrics = new AdminGrowthMetricsResponse();
        metrics.setActiveItemCount(1);
        metrics.setPendingOrderCount(1);
        metrics.setRefundedOrderCount(0);

        when(adminRewardOpsService.listItemResponses()).thenReturn(List.of(item));
        when(adminRewardOpsService.listOrderResponses()).thenReturn(List.of(order));
        when(adminRewardOpsService.metrics()).thenReturn(metrics);

        mockMvc.perform(get("/api/growth/admin/rewards/items")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemName").value("社群资格"));

        mockMvc.perform(get("/api/growth/admin/rewards/orders")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        mockMvc.perform(get("/api/growth/admin/rewards/metrics")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingOrderCount").value(1));
    }

    @Test
    void adminActionsShouldSupportItemUpsertAndOrderProcessing() throws Exception {
        AdminRewardItemResponse item = new AdminRewardItemResponse();
        item.setId(12L);
        item.setItemName("头像框周卡");
        item.setStatus("ACTIVE");

        AdminRewardOrderResponse order = new AdminRewardOrderResponse();
        order.setId(102L);
        order.setStatus("FULFILLED");
        order.setItemNameSnapshot("社群资格");

        when(adminRewardOpsService.upsertItemResponse(any(AdminRewardItemUpsertRequest.class))).thenReturn(item);
        when(adminRewardOpsService.processOrderResponse(eq(99), any(AdminRewardOrderActionRequest.class))).thenReturn(order);

        mockMvc.perform(post("/api/growth/admin/rewards/items")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "itemName": "头像框周卡",
                                  "itemDesc": "自动发放",
                                  "costBalance": 8,
                                  "stock": 5,
                                  "perUserLimit": 1,
                                  "fulfillmentMode": "AUTO",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12));

        mockMvc.perform(post("/api/growth/admin/rewards/orders/action")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId": 102,
                                  "action": "FULFILL",
                                  "note": "issued",
                                  "confirm": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FULFILLED"));
    }
}
