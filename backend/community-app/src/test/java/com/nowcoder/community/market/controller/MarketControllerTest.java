package com.nowcoder.community.market.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.market.dto.MarketAddressResponse;
import com.nowcoder.community.market.dto.MarketListingDetailResponse;
import com.nowcoder.community.market.dto.MarketListingResponse;
import com.nowcoder.community.market.dto.MarketOrderDetailResponse;
import com.nowcoder.community.market.dto.MarketOrderResponse;
import com.nowcoder.community.market.security.MarketSecurityRules;
import com.nowcoder.community.market.service.MarketAddressService;
import com.nowcoder.community.market.service.MarketDisputeService;
import com.nowcoder.community.market.service.MarketInventoryService;
import com.nowcoder.community.market.service.MarketListingService;
import com.nowcoder.community.market.service.MarketOrderService;
import com.nowcoder.community.market.service.MarketQueryService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
@Import({
        MarketController.class,
        MarketSecurityRules.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketListingService marketListingService;

    @MockBean
    private MarketInventoryService marketInventoryService;

    @MockBean
    private MarketQueryService marketQueryService;

    @MockBean
    private MarketOrderService marketOrderService;

    @MockBean
    private MarketDisputeService marketDisputeService;

    @MockBean
    private MarketAddressService marketAddressService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void publicListingApiShouldExposeUnifiedListingsWithoutAuthentication() throws Exception {
        MarketListingResponse listing = new MarketListingResponse(
                11L,
                7,
                "VIRTUAL",
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
        when(marketQueryService.listPublicListings()).thenReturn(List.of(listing));
        when(marketQueryService.getListingDetail(11L)).thenReturn(new MarketListingDetailResponse(
                11L,
                7,
                "VIRTUAL",
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

        mockMvc.perform(get("/api/market/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].goodsType").value("VIRTUAL"));

        mockMvc.perform(get("/api/market/listings/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.listingId").value(11))
                .andExpect(jsonPath("$.data.goodsType").value("VIRTUAL"));
    }

    @Test
    void authenticatedApisShouldExposeSellerBuyerDetailAndAddresses() throws Exception {
        Date now = new Date();
        MarketListingResponse sellerListing = new MarketListingResponse(
                21L,
                7,
                "PHYSICAL",
                "二手键盘",
                "九成新",
                12_900L,
                null,
                null,
                3,
                2,
                1,
                1,
                "ACTIVE"
        );
        MarketOrderResponse buyingOrder = new MarketOrderResponse(
                31L,
                "buying:req-1",
                11L,
                "VIRTUAL",
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
        MarketOrderResponse sellingOrder = new MarketOrderResponse(
                32L,
                "selling:req-1",
                12L,
                "PHYSICAL",
                7,
                10,
                1,
                12_900L,
                12_900L,
                null,
                "二手键盘",
                "SHIPPED",
                102L,
                null,
                null,
                now,
                now,
                now
        );
        MarketOrderDetailResponse detail = new MarketOrderDetailResponse(
                31L,
                "buying:req-1",
                11L,
                "VIRTUAL",
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("CODE-001"),
                null,
                now,
                now
        );
        MarketAddressResponse address = new MarketAddressResponse(
                41L,
                9,
                "张三",
                "13800000000",
                "上海市",
                "上海市",
                "浦东新区",
                "世纪大道 100 号",
                "200120",
                true,
                "ACTIVE",
                now,
                now
        );
        when(marketQueryService.listSellerListings(7)).thenReturn(List.of(sellerListing));
        when(marketQueryService.listBuyingOrders(9)).thenReturn(List.of(buyingOrder));
        when(marketQueryService.listSellingOrders(7)).thenReturn(List.of(sellingOrder));
        when(marketQueryService.getOrderDetail(31L, 9)).thenReturn(detail);
        when(marketAddressService.listAddresses(9)).thenReturn(List.of(address));

        mockMvc.perform(get("/api/market/my-listings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/market/my-listings")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("username", "seller7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].goodsType").value("PHYSICAL"));

        mockMvc.perform(get("/api/market/orders/buying")
                        .with(jwt().jwt(jwt -> jwt.subject("9").claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].goodsType").value("VIRTUAL"));

        mockMvc.perform(get("/api/market/orders/selling")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("username", "seller7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].goodsType").value("PHYSICAL"));

        mockMvc.perform(get("/api/market/orders/31")
                        .with(jwt().jwt(jwt -> jwt.subject("9").claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryContents[0]").value("CODE-001"));

        mockMvc.perform(get("/api/market/addresses")
                        .with(jwt().jwt(jwt -> jwt.subject("9").claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].receiverName").value("张三"));
    }

    @Test
    void orderDetailApiShouldReturnForbiddenWhenActorCannotAccessOrder() throws Exception {
        when(marketQueryService.getOrderDetail(31L, 8))
                .thenThrow(new BusinessException(FORBIDDEN, "market order does not belong to actor: orderId=31"));

        mockMvc.perform(get("/api/market/orders/31")
                        .with(jwt().jwt(jwt -> jwt.subject("8").claim("username", "user8"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
