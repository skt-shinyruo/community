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
import com.nowcoder.community.market.exception.MarketErrorCode;
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
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000011");
        UUID sellerUserId = uuid(7);
        MarketListingResponse listing = new MarketListingResponse(
                listingId,
                sellerUserId,
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
        when(marketQueryService.getListingDetail(listingId)).thenReturn(new MarketListingDetailResponse(
                listingId,
                sellerUserId,
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

        mockMvc.perform(get("/api/market/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.listingId").value(listingId.toString()))
                .andExpect(jsonPath("$.data.goodsType").value("VIRTUAL"));
    }

    @Test
    void authenticatedApisShouldExposeSellerBuyerDetailAndAddresses() throws Exception {
        Date now = new Date();
        UUID sellerListingId = UUID.fromString("00000000-0000-7000-8000-000000000021");
        UUID buyingOrderId = UUID.fromString("00000000-0000-7000-8000-000000000031");
        UUID buyingListingId = UUID.fromString("00000000-0000-7000-8000-000000000011");
        UUID sellingOrderId = UUID.fromString("00000000-0000-7000-8000-000000000032");
        UUID sellingListingId = UUID.fromString("00000000-0000-7000-8000-000000000012");
        UUID addressId = UUID.fromString("00000000-0000-7000-8000-000000000041");
        UUID buyingEscrowTxnId = UUID.fromString("00000000-0000-7000-8000-000000000701");
        UUID sellingEscrowTxnId = UUID.fromString("00000000-0000-7000-8000-000000000702");
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID anotherBuyerUserId = uuid(10);
        MarketListingResponse sellerListing = new MarketListingResponse(
                sellerListingId,
                sellerUserId,
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
                buyingOrderId,
                "buying:req-1",
                buyingListingId,
                "VIRTUAL",
                sellerUserId,
                buyerUserId,
                1,
                1500L,
                1500L,
                "PRELOADED",
                "Netflix 卡密",
                "DELIVERED",
                buyingEscrowTxnId,
                null,
                null,
                now,
                now,
                now
        );
        MarketOrderResponse sellingOrder = new MarketOrderResponse(
                sellingOrderId,
                "selling:req-1",
                sellingListingId,
                "PHYSICAL",
                sellerUserId,
                anotherBuyerUserId,
                1,
                12_900L,
                12_900L,
                null,
                "二手键盘",
                "SHIPPED",
                sellingEscrowTxnId,
                null,
                null,
                now,
                now,
                now
        );
        MarketOrderDetailResponse detail = new MarketOrderDetailResponse(
                buyingOrderId,
                "buying:req-1",
                buyingListingId,
                "VIRTUAL",
                sellerUserId,
                buyerUserId,
                1,
                1500L,
                1500L,
                "PRELOADED",
                "Netflix 卡密",
                "DELIVERED",
                buyingEscrowTxnId,
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
                addressId,
                buyerUserId,
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
        when(marketQueryService.listSellerListings(sellerUserId)).thenReturn(List.of(sellerListing));
        when(marketQueryService.listBuyingOrders(buyerUserId)).thenReturn(List.of(buyingOrder));
        when(marketQueryService.listSellingOrders(sellerUserId)).thenReturn(List.of(sellingOrder));
        when(marketQueryService.getOrderDetail(buyingOrderId, buyerUserId)).thenReturn(detail);
        when(marketAddressService.listAddresses(buyerUserId)).thenReturn(List.of(address));

        mockMvc.perform(get("/api/market/my-listings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/market/my-listings")
                        .with(jwt().jwt(jwt -> jwt.subject(sellerUserId.toString()).claim("username", "seller7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].goodsType").value("PHYSICAL"));

        mockMvc.perform(get("/api/market/orders/buying")
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].goodsType").value("VIRTUAL"));

        mockMvc.perform(get("/api/market/orders/selling")
                        .with(jwt().jwt(jwt -> jwt.subject(sellerUserId.toString()).claim("username", "seller7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].goodsType").value("PHYSICAL"));

        mockMvc.perform(get("/api/market/orders/" + buyingOrderId)
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryContents[0]").value("CODE-001"));

        mockMvc.perform(get("/api/market/addresses")
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].receiverName").value("张三"));
    }

    @Test
    void orderDetailApiShouldReturnForbiddenWhenActorCannotAccessOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000031");
        UUID actorUserId = uuid(8);
        when(marketQueryService.getOrderDetail(orderId, actorUserId))
                .thenThrow(new BusinessException(FORBIDDEN, "market order does not belong to actor: orderId=31"));

        mockMvc.perform(get("/api/market/orders/" + orderId)
                        .with(jwt().jwt(jwt -> jwt.subject(actorUserId.toString()).claim("username", "user8"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void createOrderApiShouldReturnConflictWhenRequestIdReplayDoesNotMatchExistingOrder() throws Exception {
        UUID buyerUserId = uuid(9);
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000011");
        UUID addressId = UUID.fromString("00000000-0000-7000-8000-000000000041");
        when(marketOrderService.createOrder("market:req-replay-conflict", buyerUserId, listingId, 1, addressId))
                .thenThrow(new BusinessException(
                        MarketErrorCode.REQUEST_REPLAY_CONFLICT,
                        "requestId replay conflict: requestId=market:req-replay-conflict"
                ));

        mockMvc.perform(post("/api/market/orders")
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "market:req-replay-conflict",
                                  "listingId": "%s",
                                  "quantity": 1,
                                  "addressId": "%s"
                                }
                                """.formatted(listingId, addressId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(MarketErrorCode.REQUEST_REPLAY_CONFLICT.getCode()));
    }
}
