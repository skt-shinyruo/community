package com.nowcoder.community.market.infrastructure.job;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.application.MarketOrderAutoConfirmApplicationService;
import com.nowcoder.community.market.application.result.MarketOrderAutoConfirmResult;
import com.nowcoder.community.market.controller.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.application.MarketAddressApplicationService;
import com.nowcoder.community.market.application.MarketListingApplicationService;
import com.nowcoder.community.market.application.MarketTestCommands;
import com.nowcoder.community.market.application.MarketWalletActionProcessorApplicationService;
import com.nowcoder.community.market.application.MarketOrderApplicationService;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService;
import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.AfterEach;
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
class MarketOrderAutoConfirmHandlerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketListingApplicationService marketListingService;

    @Autowired
    private MarketAddressApplicationService marketAddressService;

    @Autowired
    private MarketOrderApplicationService marketOrderService;

    @Autowired
    private MarketWalletActionProcessorApplicationService marketWalletActionProcessor;

    @Autowired
    private MarketOrderAutoConfirmApplicationService marketOrderAutoConfirmApplicationService;

    @Autowired
    private MarketOrderAutoConfirmHandler handler;

    @Autowired
    private WalletAccountApplicationService walletAccountService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_shipment");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_delivery");
        jdbcTemplate.update("delete from market_wallet_action");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from market_inventory_unit");
        jdbcTemplate.update("delete from market_address");
        jdbcTemplate.update("delete from market_listing");
        jdbcTemplate.update("delete from wallet_admin_action");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_account");
        XxlJobContext.setXxlJobContext(new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1));
    }

    @AfterEach
    void tearDown() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void autoConfirmShouldQueueReleaseAndProcessorShouldCompleteDeliveredAndShippedOrders() {
        UUID buyerUserId = uuid(9);
        seedBuyerBalance(buyerUserId, 50_000L);
        UUID virtualOrderId = seedDueDeliveredVirtualOrder(uuid(7), buyerUserId);
        UUID physicalOrderId = seedDueShippedPhysicalOrder(uuid(8), buyerUserId);

        MarketOrderAutoConfirmResult result = marketOrderAutoConfirmApplicationService.autoConfirmDueOrders();

        assertThat(result.completedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(orderStatus(virtualOrderId)).isEqualTo("RELEASE_PENDING");
        assertThat(orderStatus(physicalOrderId)).isEqualTo("RELEASE_PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from market_wallet_action where action_type = 'RELEASE'",
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where txn_type = 'ORDER_RELEASE'",
                Integer.class
        )).isZero();

        marketWalletActionProcessor.processDue(10);

        assertThat(orderStatus(virtualOrderId)).isEqualTo("COMPLETED");
        assertThat(orderStatus(physicalOrderId)).isEqualTo("COMPLETED");
    }

    @Test
    void handlerShouldMarkJobSuccessAfterCompletingDueOrders() {
        UUID buyerUserId = uuid(9);
        seedBuyerBalance(buyerUserId, 50_000L);
        UUID virtualOrderId = seedDueDeliveredVirtualOrder(uuid(7), buyerUserId);
        UUID physicalOrderId = seedDueShippedPhysicalOrder(uuid(8), buyerUserId);

        handler.autoConfirm();

        assertThat(XxlJobContext.getXxlJobContext().getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
        assertThat(orderStatus(virtualOrderId)).isEqualTo("RELEASE_PENDING");
        assertThat(orderStatus(physicalOrderId)).isEqualTo("RELEASE_PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from market_wallet_action where action_type = 'RELEASE'",
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where txn_type = 'ORDER_RELEASE'",
                Integer.class
        )).isZero();

        marketWalletActionProcessor.processDue(10);

        assertThat(orderStatus(virtualOrderId)).isEqualTo("COMPLETED");
        assertThat(orderStatus(physicalOrderId)).isEqualTo("COMPLETED");
    }

    private UUID seedDueDeliveredVirtualOrder(UUID sellerUserId, UUID buyerUserId) {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("VIRTUAL");
        request.setTitle("邀请码");
        request.setDescription("手工交付");
        request.setUnitPrice(1_200L);
        request.setDeliveryMode("MANUAL");
        request.setStockMode("FINITE");
        request.setStockTotal(2);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(2);
        UUID listingId = marketListingService.createListing(MarketTestCommands.listingCommand(sellerUserId, request, null)).listingId();
        UUID orderId = marketOrderService.createOrder("auto-confirm:virtual:req-1", buyerUserId, listingId, 1, null).orderId();
        marketWalletActionProcessor.processDue(10);
        marketOrderService.deliverVirtualOrder(orderId, sellerUserId, "邀请码-A");
        jdbcTemplate.update(
                "update market_order set auto_confirm_at = timestampadd('HOUR', -1, current_timestamp) where order_id = ?",
                orderId
        );
        return orderId;
    }

    private UUID seedDueShippedPhysicalOrder(UUID sellerUserId, UUID buyerUserId) {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("PHYSICAL");
        request.setTitle("二手键盘");
        request.setDescription("九成新");
        request.setUnitPrice(12_900L);
        request.setStockTotal(1);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);
        UUID listingId = marketListingService.createListing(MarketTestCommands.listingCommand(sellerUserId, request, null)).listingId();

        CreateMarketAddressRequest addressRequest = new CreateMarketAddressRequest();
        addressRequest.setReceiverName("张三");
        addressRequest.setReceiverPhone("13800000000");
        addressRequest.setProvince("上海市");
        addressRequest.setCity("上海市");
        addressRequest.setDistrict("浦东新区");
        addressRequest.setDetailAddress("世纪大道 100 号");
        addressRequest.setPostalCode("200120");
        addressRequest.setDefaultAddress(true);
        UUID addressId = marketAddressService.createAddress(MarketTestCommands.addressCommand(buyerUserId, addressRequest)).addressId();

        UUID orderId = marketOrderService.createOrder("auto-confirm:physical:req-1", buyerUserId, listingId, 1, addressId).orderId();
        marketWalletActionProcessor.processDue(10);
        marketOrderService.shipPhysicalOrder(orderId, sellerUserId, "顺丰", "SF1234567890", "工作日派送");
        jdbcTemplate.update(
                "update market_order set auto_confirm_at = timestampadd('DAY', -1, current_timestamp) where order_id = ?",
                orderId
        );
        return orderId;
    }

    private void seedBuyerBalance(UUID userId, long balance) {
        UUID accountId = walletAccountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }

    private String orderStatus(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select status from market_order where order_id = ?",
                String.class,
                orderId
        );
    }
}
