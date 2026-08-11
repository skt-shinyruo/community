package com.nowcoder.community.market.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.market.application.MarketAddressApplicationService.CreateMarketAddressCommand;
import com.nowcoder.community.market.application.result.MarketAddressResult;
import com.nowcoder.community.market.application.result.MarketListingDetailResult;
import com.nowcoder.community.market.application.result.MarketListingResult;
import com.nowcoder.community.market.exception.MarketErrorCode;
import com.nowcoder.community.market.application.result.MarketOrderDetailResult;
import com.nowcoder.community.market.application.result.MarketOrderResult;
import com.nowcoder.community.market.application.result.MarketPageResult;
import com.nowcoder.community.market.application.result.MarketOrderDetailResult.ShipmentResult;
import com.nowcoder.community.market.security.MarketSecurityRules;
import com.nowcoder.community.market.application.MarketAddressApplicationService;
import com.nowcoder.community.market.application.MarketDisputeApplicationService;
import com.nowcoder.community.market.application.MarketInventoryApplicationService;
import com.nowcoder.community.market.application.MarketListingApplicationService;
import com.nowcoder.community.market.application.MarketOrderApplicationService;
import com.nowcoder.community.market.application.MarketQueryApplicationService;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
        WebMvcSliceJsonCodecTestConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketListingApplicationService marketListingService;

    @MockitoBean
    private MarketInventoryApplicationService marketInventoryService;

    @MockitoBean
    private MarketQueryApplicationService marketQueryService;

    @MockitoBean
    private MarketOrderApplicationService marketOrderService;

    @MockitoBean
    private MarketDisputeApplicationService marketDisputeService;

    @MockitoBean
    private MarketAddressApplicationService marketAddressService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void publicListingApiShouldExposeUnifiedListingsWithoutAuthentication() throws Exception {
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000011");
        UUID sellerUserId = uuid(7);
        MarketListingResult listing = new MarketListingResult(
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
        when(marketQueryService.listPublicListings(2, 1))
                .thenReturn(new MarketPageResult<>(List.of(listing), true, 2, 1));
        when(marketQueryService.getListingDetail(listingId)).thenReturn(new MarketListingDetailResult(
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

        mockMvc.perform(get("/api/market/listings").param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].goodsType").value("VIRTUAL"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(1));

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
        MarketListingResult sellerListing = new MarketListingResult(
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
        MarketOrderResult buyingOrder = new MarketOrderResult(
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
        MarketOrderResult sellingOrder = new MarketOrderResult(
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
        MarketOrderDetailResult detail = new MarketOrderDetailResult(
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
                new ShipmentResult(
                        uuid(301),
                        buyingOrderId,
                        sellerUserId,
                        "SF",
                        "SF-2026-001",
                        "fragile",
                        now,
                        now,
                        now
                ),
                now,
                now
        );
        MarketAddressResult address = new MarketAddressResult(
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
        when(marketQueryService.listSellerListings(sellerUserId, null, null))
                .thenReturn(new MarketPageResult<>(List.of(sellerListing), false, 0, 20));
        when(marketQueryService.listBuyingOrders(buyerUserId, null, null))
                .thenReturn(new MarketPageResult<>(List.of(buyingOrder), false, 0, 20));
        when(marketQueryService.listSellingOrders(sellerUserId, null, null))
                .thenReturn(new MarketPageResult<>(List.of(sellingOrder), false, 0, 20));
        when(marketQueryService.getOrderDetail(buyingOrderId, buyerUserId)).thenReturn(detail);
        when(marketAddressService.listAddresses(buyerUserId)).thenReturn(List.of(address));

        mockMvc.perform(get("/api/market/my-listings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/market/my-listings")
                        .with(jwt().jwt(jwt -> jwt.subject(sellerUserId.toString()).claim("username", "seller7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].goodsType").value("PHYSICAL"));

        mockMvc.perform(get("/api/market/orders/buying")
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].goodsType").value("VIRTUAL"));

        mockMvc.perform(get("/api/market/orders/selling")
                        .with(jwt().jwt(jwt -> jwt.subject(sellerUserId.toString()).claim("username", "seller7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].goodsType").value("PHYSICAL"));

        mockMvc.perform(get("/api/market/orders/" + buyingOrderId)
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryContents[0]").value("CODE-001"))
                .andExpect(jsonPath("$.data.shipment.trackingNo").value("SF-2026-001"))
                .andExpect(jsonPath("$.data.shipment.shippingRemark").value("fragile"));

        mockMvc.perform(get("/api/market/addresses")
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].receiverName").value("张三"))
                .andExpect(jsonPath("$.data[0].defaultAddress").value(true))
                .andExpect(jsonPath("$.data[0].isDefault").doesNotExist());
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
    void createOrderApiShouldAcceptIdempotencyKeyHeaderWithoutBodyRequestId() throws Exception {
        Date now = new Date();
        UUID buyerUserId = uuid(9);
        UUID sellerUserId = uuid(7);
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000011");
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000051");
        UUID escrowTxnId = UUID.fromString("00000000-0000-7000-8000-000000000701");

        when(marketOrderService.createOrder(any()))
                .thenReturn(new MarketOrderResult(
                        orderId,
                        "market:header-api-1",
                        listingId,
                        "VIRTUAL",
                        sellerUserId,
                        buyerUserId,
                        1,
                        1200L,
                        1200L,
                        "MANUAL",
                        "邀请码",
                        "ESCROWED",
                        escrowTxnId,
                        null,
                        null,
                        null,
                        now,
                        now
                ));

        mockMvc.perform(post("/api/market/orders")
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "market:header-api-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "listingId": "%s",
                                  "quantity": 1
                                }
                                """.formatted(listingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value("market:header-api-1"));
    }

    @Test
    void createAddressApiShouldBindDefaultAddressPayload() throws Exception {
        UUID buyerUserId = uuid(9);
        UUID addressId = UUID.fromString("00000000-0000-7000-8000-000000000041");
        when(marketAddressService.createAddress(any(CreateMarketAddressCommand.class)))
                .thenReturn(new MarketAddressResult(
                        addressId,
                        buyerUserId,
                        "李四",
                        "13900000000",
                        "北京市",
                        "北京市",
                        "海淀区",
                        "中关村 1 号",
                        "100080",
                        true,
                        "ACTIVE",
                        new Date(),
                        new Date()
                ));

        mockMvc.perform(post("/api/market/addresses")
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "receiverName": "李四",
                                  "receiverPhone": "13900000000",
                                  "province": "北京市",
                                  "city": "北京市",
                                  "district": "海淀区",
                                  "detailAddress": "中关村 1 号",
                                  "postalCode": "100080",
                                  "defaultAddress": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(true));

        var commandCaptor = forClass(CreateMarketAddressCommand.class);
        verify(marketAddressService).createAddress(commandCaptor.capture());
        assertTrue(commandCaptor.getValue().defaultAddress());
    }

    @Test
    void createOrderApiShouldRejectBodyRequestId() throws Exception {
        UUID buyerUserId = uuid(9);
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000011");

        mockMvc.perform(post("/api/market/orders")
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "market:req-body")
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "market:req-body",
                                  "listingId": "%s",
                                  "quantity": 1
                                }
                                """.formatted(listingId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createOrderApiShouldReturnConflictWhenRequestIdReplayDoesNotMatchExistingOrder() throws Exception {
        UUID buyerUserId = uuid(9);
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000011");
        UUID addressId = UUID.fromString("00000000-0000-7000-8000-000000000041");
        when(marketOrderService.createOrder(any()))
                .thenThrow(new BusinessException(
                        MarketErrorCode.REQUEST_REPLAY_CONFLICT,
                        "requestId replay conflict: requestId=market:req-replay-conflict"
                ));

        mockMvc.perform(post("/api/market/orders")
                        .with(jwt().jwt(jwt -> jwt.subject(buyerUserId.toString()).claim("username", "buyer9")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "market:req-replay-conflict")
                        .contentType("application/json")
                        .content("""
                                {
                                  "listingId": "%s",
                                  "quantity": 1,
                                  "addressId": "%s"
                                }
                                """.formatted(listingId, addressId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(MarketErrorCode.REQUEST_REPLAY_CONFLICT.getCode()));
    }
}
