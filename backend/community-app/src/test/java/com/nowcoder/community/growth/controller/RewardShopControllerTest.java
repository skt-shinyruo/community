package com.nowcoder.community.growth.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.growth.dto.RewardItemResponse;
import com.nowcoder.community.growth.dto.RewardOrderResponse;
import com.nowcoder.community.growth.service.RewardCatalogService;
import com.nowcoder.community.growth.service.RewardOrderQueryService;
import com.nowcoder.community.growth.service.RewardRedemptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
class RewardShopControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RewardCatalogService rewardCatalogService;

    @MockBean
    private RewardOrderQueryService rewardOrderQueryService;

    @MockBean
    private RewardRedemptionService rewardRedemptionService;

    @Test
    void rewardShopApisShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/growth/shop/items"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/growth/shop/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/growth/shop/redeem")
                        .contentType("application/json")
                        .content("{\"itemId\":1,\"requestId\":\"redeem-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rewardShopApisShouldExposeCatalogDetailAndOrderHistory() throws Exception {
        RewardItemResponse item = new RewardItemResponse();
        item.setId(11L);
        item.setItemName("头像框周卡");
        item.setItemDesc("一周头像框权益");
        item.setCostBalance(12);
        item.setStock(5);
        item.setPerUserLimit(1);
        item.setFulfillmentMode("AUTO");
        item.setStatus("ACTIVE");

        RewardOrderResponse order = new RewardOrderResponse();
        order.setId(101L);
        order.setItemId(11L);
        order.setStatus("FULFILLED");
        order.setCostBalanceSnapshot(12);
        order.setFulfillmentModeSnapshot("AUTO");
        order.setItemNameSnapshot("头像框周卡");
        order.setItemDescSnapshot("一周头像框权益");

        when(rewardCatalogService.listItemResponsesForUser(1)).thenReturn(List.of(item));
        when(rewardCatalogService.getItemResponseForUser(1, 11L)).thenReturn(item);
        when(rewardOrderQueryService.listOrderResponsesForUser(1)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/growth/shop/items")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].itemName").value("头像框周卡"))
                .andExpect(jsonPath("$.data[0].perUserLimit").value(1));

        mockMvc.perform(get("/api/growth/shop/items/11")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.fulfillmentMode").value("AUTO"));

        mockMvc.perform(get("/api/growth/shop/orders")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].status").value("FULFILLED"))
                .andExpect(jsonPath("$.data[0].itemNameSnapshot").value("头像框周卡"));
    }

    @Test
    void redeemApiShouldReturnCreatedOrExistingOrderView() throws Exception {
        RewardOrderResponse order = new RewardOrderResponse();
        order.setId(102L);
        order.setItemId(12L);
        order.setStatus("PENDING");
        order.setCostBalanceSnapshot(15);
        order.setFulfillmentModeSnapshot("MANUAL");
        order.setItemNameSnapshot("社群资格");
        order.setItemDescSnapshot("人工发放社群资格");

        when(rewardRedemptionService.redeemResponse(eq(1), eq(12L), eq("redeem-req-api-1"))).thenReturn(order);

        mockMvc.perform(post("/api/growth/shop/redeem")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "itemId": 12,
                                  "requestId": "redeem-req-api-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(102))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.fulfillmentModeSnapshot").value("MANUAL"));
    }

    @Test
    void redeemApiShouldRejectMissingItemIdAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/growth/shop/redeem")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "redeem-req-api-missing-item"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
