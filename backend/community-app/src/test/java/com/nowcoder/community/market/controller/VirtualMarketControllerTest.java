package com.nowcoder.community.market.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.market.dto.AddVirtualInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateVirtualListingRequest;
import com.nowcoder.community.market.dto.VirtualListingDetailResponse;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import com.nowcoder.community.market.dto.VirtualOrderDetailResponse;
import com.nowcoder.community.market.dto.VirtualOrderResponse;
import com.nowcoder.community.market.security.MarketSecurityRules;
import com.nowcoder.community.market.service.VirtualDisputeService;
import com.nowcoder.community.market.service.VirtualInventoryService;
import com.nowcoder.community.market.service.VirtualListingService;
import com.nowcoder.community.market.service.VirtualMarketQueryService;
import com.nowcoder.community.market.service.VirtualOrderService;
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

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VirtualMarketController.class)
@Import({
        VirtualMarketController.class,
        MarketSecurityRules.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class VirtualMarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VirtualListingService virtualListingService;

    @MockBean
    private VirtualInventoryService virtualInventoryService;

    @MockBean
    private VirtualMarketQueryService virtualMarketQueryService;

    @MockBean
    private VirtualOrderService virtualOrderService;

    @MockBean
    private VirtualDisputeService virtualDisputeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void publicListingApiShouldExposeActiveListingsWithoutAuthentication() throws Exception {
        VirtualListingResponse listing = new VirtualListingResponse(
                11L,
                7,
                "Netflix 卡密",
                "自动交付",
                1500L,
                "PRELOADED",
                "FINITE",
                2,
                2,
                1,
                2,
                "ACTIVE"
        );
        when(virtualMarketQueryService.listPublicListings()).thenReturn(List.of(listing));
        when(virtualMarketQueryService.getListingDetail(11L)).thenReturn(new VirtualListingDetailResponse(
                11L,
                7,
                "Netflix 卡密",
                "自动交付",
                1500L,
                "PRELOADED",
                "FINITE",
                2,
                2,
                1,
                2,
                "ACTIVE",
                new Date(),
                new Date()
        ));

        mockMvc.perform(get("/api/market/virtual/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].title").value("Netflix 卡密"));

        mockMvc.perform(get("/api/market/virtual/listings/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.listingId").value(11))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void myListingsApiShouldRequireAuthenticationAndReturnSellerListings() throws Exception {
        VirtualListingResponse listing = new VirtualListingResponse(
                21L,
                7,
                "Steam 兑换码",
                "库存页继续维护卡密",
                1999L,
                "PRELOADED",
                "FINITE",
                3,
                2,
                1,
                1,
                "ACTIVE"
        );
        when(virtualMarketQueryService.listSellerListings(7)).thenReturn(List.of(listing));

        mockMvc.perform(get("/api/market/virtual/my-listings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/market/virtual/my-listings")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("username", "seller7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].listingId").value(21))
                .andExpect(jsonPath("$.data[0].title").value("Steam 兑换码"))
                .andExpect(jsonPath("$.data[0].stockAvailable").value(2));
    }

    @Test
    void createListingApiShouldRequireAuthenticationAndReturnCreatedListing() throws Exception {
        VirtualListingResponse listing = new VirtualListingResponse(
                11L,
                7,
                "Netflix 卡密",
                "自动交付",
                1500L,
                "PRELOADED",
                "FINITE",
                2,
                2,
                1,
                2,
                "ACTIVE"
        );
        when(virtualListingService.createListing(eq(7), any(CreateVirtualListingRequest.class), any(AddVirtualInventoryBatchRequest.class)))
                .thenReturn(listing);

        mockMvc.perform(post("/api/market/virtual/listings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Netflix 卡密",
                                  "description": "自动交付",
                                  "unitPrice": 1500,
                                  "deliveryMode": "PRELOADED",
                                  "stockMode": "FINITE",
                                  "stockTotal": 2,
                                  "minPurchaseQuantity": 1,
                                  "maxPurchaseQuantity": 2,
                                  "inventory": {
                                    "payloadType": "CODE",
                                    "payloads": ["NFX-001", "NFX-002"]
                                  }
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/market/virtual/listings")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("username", "seller7")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Netflix 卡密",
                                  "description": "自动交付",
                                  "unitPrice": 1500,
                                  "deliveryMode": "PRELOADED",
                                  "stockMode": "FINITE",
                                  "stockTotal": 2,
                                  "minPurchaseQuantity": 1,
                                  "maxPurchaseQuantity": 2,
                                  "inventory": {
                                    "payloadType": "CODE",
                                    "payloads": ["NFX-001", "NFX-002"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.listingId").value(11))
                .andExpect(jsonPath("$.data.stockAvailable").value(2));
    }

    @Test
    void orderQueryApisShouldRequireAuthenticationAndExposeBuyerSellerAndDetailPayloads() throws Exception {
        Date now = new Date();
        VirtualOrderResponse buyingOrder = new VirtualOrderResponse(
                31L,
                "buying:req-1",
                11L,
                7,
                9,
                1,
                1500L,
                1500L,
                "PRELOADED",
                "Netflix 卡密",
                "DELIVERED",
                101L,
                null,
                null,
                now,
                now,
                now
        );
        VirtualOrderResponse sellingOrder = new VirtualOrderResponse(
                32L,
                "selling:req-1",
                12L,
                7,
                10,
                2,
                1200L,
                2400L,
                "MANUAL",
                "邀请码",
                "ESCROWED",
                102L,
                null,
                null,
                null,
                now,
                now
        );
        VirtualOrderDetailResponse detail = new VirtualOrderDetailResponse(
                31L,
                "buying:req-1",
                11L,
                7,
                9,
                1,
                1500L,
                1500L,
                "PRELOADED",
                "Netflix 卡密",
                "DELIVERED",
                101L,
                null,
                null,
                now,
                now,
                now,
                List.of("CODE-001", "CODE-002")
        );
        when(virtualMarketQueryService.listBuyingOrders(9)).thenReturn(List.of(buyingOrder));
        when(virtualMarketQueryService.listSellingOrders(7)).thenReturn(List.of(sellingOrder));
        when(virtualMarketQueryService.getOrderDetail(31L, 9)).thenReturn(detail);
        when(virtualMarketQueryService.getOrderDetail(31L, 7)).thenReturn(detail);

        mockMvc.perform(get("/api/market/virtual/orders/buying"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/market/virtual/orders/buying")
                        .with(jwt().jwt(jwt -> jwt.subject("9").claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].requestId").value("buying:req-1"))
                .andExpect(jsonPath("$.data[0].buyerUserId").value(9))
                .andExpect(jsonPath("$.data[0].status").value("DELIVERED"));

        mockMvc.perform(get("/api/market/virtual/orders/selling")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("username", "seller7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].requestId").value("selling:req-1"))
                .andExpect(jsonPath("$.data[0].sellerUserId").value(7))
                .andExpect(jsonPath("$.data[0].status").value("ESCROWED"));

        mockMvc.perform(get("/api/market/virtual/orders/31")
                        .with(jwt().jwt(jwt -> jwt.subject("9").claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(31))
                .andExpect(jsonPath("$.data.deliveryContents[0]").value("CODE-001"))
                .andExpect(jsonPath("$.data.deliveryContents[1]").value("CODE-002"));

        mockMvc.perform(get("/api/market/virtual/orders/31")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("username", "seller7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(31))
                .andExpect(jsonPath("$.data.deliveryContents[0]").value("CODE-001"));
    }

    @Test
    void orderDetailApiShouldReturnForbiddenWhenActorCannotAccessOrder() throws Exception {
        when(virtualMarketQueryService.getOrderDetail(31L, 8))
                .thenThrow(new BusinessException(FORBIDDEN, "virtual order does not belong to actor: orderId=31"));

        mockMvc.perform(get("/api/market/virtual/orders/31")
                        .with(jwt().jwt(jwt -> jwt.subject("8").claim("username", "user8"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
