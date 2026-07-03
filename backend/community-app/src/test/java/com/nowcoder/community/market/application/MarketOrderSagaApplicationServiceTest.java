package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketListingDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketInventoryUnitMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketListingMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
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
class MarketOrderSagaApplicationServiceTest {

    private final UUID sellerUserId = uuid(7);
    private final UUID buyerUserId = uuid(9);
    private final UUID escrowTxnId = uuid(201);
    private final UUID refundTxnId = uuid(202);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketOrderSagaApplicationService sagaService;

    @Autowired
    private MarketListingMapper marketListingMapper;

    @Autowired
    private MarketOrderMapper marketOrderMapper;

    @Autowired
    private MarketInventoryUnitMapper marketInventoryUnitMapper;

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

    @Test
    void markRefundSucceededShouldNotRestoreStockForDisputeRefundedPhysicalOrder() {
        seedFiniteStockOrder("DISPUTE_REFUND_PENDING", 0);

        assertThat(sagaService.markRefundSucceeded(orderId, refundTxnId)).isTrue();

        Integer stock = jdbcTemplate.queryForObject(
                "select stock_available from market_listing where listing_id = ?",
                Integer.class,
                listingId
        );
        assertThat(stock).isEqualTo(0);
        assertThat(order(orderId).getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void markRefundSucceededShouldNotRestorePreloadedDeliveredInventoryAsAvailableStock() {
        seedPreloadedDeliveredDisputeRefundOrder();

        assertThat(sagaService.markRefundSucceeded(orderId, refundTxnId)).isTrue();

        Integer stock = jdbcTemplate.queryForObject(
                "select stock_available from market_listing where listing_id = ?",
                Integer.class,
                listingId
        );
        String inventoryStatus = jdbcTemplate.queryForObject(
                "select status from market_inventory_unit where reserved_order_id = ?",
                String.class,
                orderId
        );
        assertThat(stock).isEqualTo(0);
        assertThat(inventoryStatus).isEqualTo("DELIVERED");
        assertThat(order(orderId).getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void markDeliveredShouldRequireEscrowedStatusInPersistence() {
        seedFiniteStockOrder("REFUND_PENDING", 0);

        int updated = marketOrderMapper.markDelivered(orderId, new java.util.Date());

        assertThat(updated).isZero();
        assertThat(order(orderId).getStatus()).isEqualTo("REFUND_PENDING");
    }

    @Test
    void markShippedShouldRequireEscrowedStatusInPersistence() {
        seedFiniteStockOrder("REFUND_PENDING", 0);

        int updated = marketOrderMapper.markShipped(orderId, new java.util.Date());

        assertThat(updated).isZero();
        assertThat(order(orderId).getStatus()).isEqualTo("REFUND_PENDING");
    }

    @Test
    void markDisputedShouldRequireDeliveredOrShippedStatusInPersistence() {
        seedFiniteStockOrder("RELEASE_PENDING", 0);

        int updated = marketOrderMapper.markDisputed(orderId);

        assertThat(updated).isZero();
        assertThat(order(orderId).getStatus()).isEqualTo("RELEASE_PENDING");
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
        marketListingMapper.insert(MarketListingDataObject.from(listing));

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
        marketOrderMapper.insert(MarketOrderDataObject.from(order));
    }

    private void seedPreloadedDeliveredDisputeRefundOrder() {
        listingId = uuid(6001);
        orderId = uuid(6002);
        UUID inventoryUnitId = uuid(6003);

        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType("VIRTUAL");
        listing.setTitle("序列号");
        listing.setDescription("自动交付");
        listing.setUnitPrice(12_900L);
        listing.setDeliveryMode("PRELOADED");
        listing.setStockMode("FINITE");
        listing.setStockTotal(1);
        listing.setStockAvailable(0);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus("SOLD_OUT");
        marketListingMapper.insert(MarketListingDataObject.from(listing));

        jdbcTemplate.update(
                "insert into market_inventory_unit(inventory_unit_id, listing_id, seller_user_id, payload_type, payload_content, status, reserved_order_id, delivered_at) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                inventoryUnitId,
                listingId,
                sellerUserId,
                "TEXT",
                "CODE-001",
                "DELIVERED",
                orderId
        );

        MarketOrder order = new MarketOrder();
        order.setOrderId(orderId);
        order.setRequestId("saga:preloaded-dispute-refund");
        order.setListingId(listingId);
        order.setGoodsType("VIRTUAL");
        order.setSellerUserId(sellerUserId);
        order.setBuyerUserId(buyerUserId);
        order.setQuantity(1);
        order.setUnitPriceSnapshot(12_900L);
        order.setTotalAmount(12_900L);
        order.setDeliveryModeSnapshot("PRELOADED");
        order.setListingTitleSnapshot("序列号");
        order.setStatus("DISPUTE_REFUND_PENDING");
        marketOrderMapper.insert(MarketOrderDataObject.from(order));
    }
}
