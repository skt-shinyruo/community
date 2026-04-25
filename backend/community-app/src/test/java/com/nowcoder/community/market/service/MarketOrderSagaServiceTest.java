package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketOrderSagaServiceTest {

    private final UUID sellerUserId = uuid(7);
    private final UUID buyerUserId = uuid(9);
    private final UUID escrowTxnId = uuid(201);
    private final UUID refundTxnId = uuid(202);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketOrderSagaService sagaService;

    @Autowired
    private MarketListingMapper marketListingMapper;

    @Autowired
    private MarketOrderMapper marketOrderMapper;

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
    void markEscrowSucceededShouldAdvanceOnlyFromEscrowPending() {
        seedOrder("ESCROW_PENDING");

        boolean advanced = sagaService.markEscrowSucceeded(orderId, escrowTxnId);

        assertThat(advanced).isTrue();
        assertThat(order(orderId).getStatus()).isEqualTo("ESCROWED");
        assertThat(order(orderId).getEscrowTxnId()).isEqualTo(escrowTxnId);
    }

    @Test
    void markEscrowSucceededShouldNotOverwriteCancelledOrder() {
        seedOrder("ESCROW_CANCEL_PENDING");

        boolean advanced = sagaService.markEscrowSucceeded(orderId, escrowTxnId);

        assertThat(advanced).isFalse();
        assertThat(order(orderId).getEscrowTxnId()).isNull();
        assertThat(order(orderId).getStatus()).isEqualTo("ESCROW_CANCEL_PENDING");
    }

    @Test
    void markRefundSucceededShouldRestoreInventoryOnce() {
        seedFiniteStockOrder("REFUND_PENDING", 0);

        assertThat(sagaService.markRefundSucceeded(orderId, refundTxnId)).isTrue();
        sagaService.markRefundSucceeded(orderId, refundTxnId);

        Integer stock = jdbcTemplate.queryForObject(
                "select stock_available from market_listing where listing_id = ?",
                Integer.class,
                listingId
        );
        assertThat(stock).isEqualTo(1);
        assertThat(order(orderId).getStatus()).isEqualTo("CANCELLED");
        assertThat(order(orderId).getRefundTxnId()).isEqualTo(refundTxnId);
    }

    private MarketOrder order(UUID id) {
        return marketOrderMapper.selectById(id);
    }

    private void seedOrder(String status) {
        seedFiniteStockOrder(status, 3);
    }

    private void seedFiniteStockOrder(String status, int stockAvailable) {
        listingId = uuid(Math.abs(status.hashCode() % 10_000) + 1);
        orderId = uuid(Math.abs((status + ":order").hashCode() % 10_000) + 1);

        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType("PHYSICAL");
        listing.setTitle("二手键盘");
        listing.setDescription("九成新");
        listing.setUnitPrice(12_900L);
        listing.setStockMode("FINITE");
        listing.setStockTotal(3);
        listing.setStockAvailable(stockAvailable);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus(stockAvailable == 0 ? "SOLD_OUT" : "ACTIVE");
        marketListingMapper.insert(listing);

        MarketOrder order = new MarketOrder();
        order.setOrderId(orderId);
        order.setRequestId("saga:" + status.toLowerCase());
        order.setListingId(listingId);
        order.setGoodsType("PHYSICAL");
        order.setSellerUserId(sellerUserId);
        order.setBuyerUserId(buyerUserId);
        order.setQuantity(1);
        order.setUnitPriceSnapshot(12_900L);
        order.setTotalAmount(12_900L);
        order.setDeliveryModeSnapshot("MANUAL");
        order.setListingTitleSnapshot("二手键盘");
        order.setStatus(status);
        marketOrderMapper.insert(order);
    }
}
