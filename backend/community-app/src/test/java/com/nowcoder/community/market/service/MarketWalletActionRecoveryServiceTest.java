package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.entity.MarketWalletAction;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
import com.nowcoder.community.market.mapper.MarketWalletActionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketWalletActionRecoveryServiceTest {

    private final UUID sellerUserId = uuid(7);
    private final UUID buyerUserId = uuid(9);
    private final UUID refundTxnId = uuid(202);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketWalletActionRecoveryService recoveryService;

    @Autowired
    private MarketListingMapper marketListingMapper;

    @Autowired
    private MarketOrderMapper marketOrderMapper;

    @Autowired
    private MarketWalletActionMapper marketWalletActionMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    private UUID listingId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_wallet_action");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from market_inventory_unit");
        jdbcTemplate.update("delete from market_listing");
    }

    @Test
    void recoverExpiredProcessingShouldReturnActionToRetrying() {
        UUID actionId = seedProcessingActionWithExpiredLease(Instant.parse("2026-04-25T09:00:00Z"));

        int recovered = recoveryService.recoverExpiredProcessing(Instant.parse("2026-04-25T10:00:00Z"));

        assertThat(recovered).isEqualTo(1);
        assertThat(marketWalletActionMapper.selectById(actionId).getStatus()).isEqualTo("RETRYING");
    }

    @Test
    void reconcileWalletTxnWithoutSucceededActionShouldAdvanceRemainingMarketState() {
        seedRefundPendingOrder();
        seedRefundActionWithWalletTxn();

        MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(50);

        assertThat(result.reconciledCount()).isEqualTo(1);
        assertThat(marketOrderMapper.selectById(orderId).getStatus()).isEqualTo("CANCELLED");
        assertThat(marketWalletActionMapper.selectByOrderAndType(orderId, "REFUND").getStatus()).isEqualTo("SUCCEEDED");
    }

    private UUID seedProcessingActionWithExpiredLease(Instant expiredAt) {
        UUID actionId = uuid(301);
        UUID actionOrderId = uuid(302);
        MarketWalletAction action = action(actionId, actionOrderId, "RELEASE");
        action.setStatus("PROCESSING");
        action.setProcessingLeaseUntil(Date.from(expiredAt));
        marketWalletActionMapper.insert(action);
        return actionId;
    }

    private void seedRefundPendingOrder() {
        listingId = uuid(401);
        orderId = uuid(402);

        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType("PHYSICAL");
        listing.setTitle("二手键盘");
        listing.setDescription("九成新");
        listing.setUnitPrice(12_900L);
        listing.setStockMode("FINITE");
        listing.setStockTotal(1);
        listing.setStockAvailable(0);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus("SOLD_OUT");
        marketListingMapper.insert(listing);

        MarketOrder order = new MarketOrder();
        order.setOrderId(orderId);
        order.setRequestId("recovery:refund-pending");
        order.setListingId(listingId);
        order.setGoodsType("PHYSICAL");
        order.setSellerUserId(sellerUserId);
        order.setBuyerUserId(buyerUserId);
        order.setQuantity(1);
        order.setUnitPriceSnapshot(12_900L);
        order.setTotalAmount(12_900L);
        order.setDeliveryModeSnapshot("MANUAL");
        order.setListingTitleSnapshot("二手键盘");
        order.setStatus("REFUND_PENDING");
        marketOrderMapper.insert(order);
    }

    private void seedRefundActionWithWalletTxn() {
        MarketWalletAction action = action(uuid(403), orderId, "REFUND");
        action.setStatus("PROCESSING");
        action.setWalletTxnId(refundTxnId);
        marketWalletActionMapper.insert(action);
    }

    private MarketWalletAction action(UUID actionId, UUID actionOrderId, String actionType) {
        MarketWalletAction action = new MarketWalletAction();
        action.setActionId(actionId);
        action.setOrderId(actionOrderId);
        action.setActionType(actionType);
        action.setRequestId("market-order:" + actionOrderId + ":" + actionType.toLowerCase());
        action.setWalletBizId("market-order:" + actionOrderId);
        action.setActorUserId("RELEASE".equals(actionType) ? sellerUserId : buyerUserId);
        action.setCounterpartyUserId("RELEASE".equals(actionType) ? buyerUserId : sellerUserId);
        action.setAmount(12_900L);
        action.setStatus("PENDING");
        return action;
    }
}
