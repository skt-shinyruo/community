package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.api.action.MarketOrderAutoConfirmActionApi;
import com.nowcoder.community.market.api.model.MarketOrderAutoConfirmResult;
import com.nowcoder.community.market.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.service.MarketAddressService;
import com.nowcoder.community.market.service.MarketListingService;
import com.nowcoder.community.market.service.MarketOrderService;
import com.nowcoder.community.wallet.service.WalletAccountService;
import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
    private MarketListingService marketListingService;

    @Autowired
    private MarketAddressService marketAddressService;

    @Autowired
    private MarketOrderService marketOrderService;

    @Autowired
    private MarketOrderAutoConfirmActionApi marketOrderAutoConfirmActionApi;

    @Autowired
    private MarketOrderAutoConfirmHandler handler;

    @Autowired
    private WalletAccountService walletAccountService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_shipment");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_delivery");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from market_inventory_unit");
        jdbcTemplate.update("delete from market_address");
        jdbcTemplate.update("delete from market_listing");
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
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
    void autoConfirmShouldCompleteDeliveredAndShippedOrdersIdempotently() {
        seedBuyerBalance(9, 50_000L);
        long virtualOrderId = seedDueDeliveredVirtualOrder(7, 9);
        long physicalOrderId = seedDueShippedPhysicalOrder(8, 9);

        MarketOrderAutoConfirmResult result = marketOrderAutoConfirmActionApi.autoConfirmDueOrders();

        assertThat(result.completedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(orderStatus(virtualOrderId)).isEqualTo("COMPLETED");
        assertThat(orderStatus(physicalOrderId)).isEqualTo("COMPLETED");
    }

    @Test
    void handlerShouldMarkJobSuccessAfterCompletingDueOrders() {
        seedBuyerBalance(9, 50_000L);
        seedDueDeliveredVirtualOrder(7, 9);
        seedDueShippedPhysicalOrder(8, 9);

        handler.autoConfirm();

        assertThat(XxlJobContext.getXxlJobContext().getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    }

    private long seedDueDeliveredVirtualOrder(int sellerUserId, int buyerUserId) {
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
        long listingId = marketListingService.createListing(sellerUserId, request, null).listingId();
        long orderId = marketOrderService.createOrder("auto-confirm:virtual:req-1", buyerUserId, listingId, 1, null).orderId();
        marketOrderService.deliverVirtualOrder(orderId, sellerUserId, "邀请码-A");
        jdbcTemplate.update(
                "update market_order set auto_confirm_at = timestampadd('HOUR', -1, current_timestamp) where order_id = ?",
                orderId
        );
        return orderId;
    }

    private long seedDueShippedPhysicalOrder(int sellerUserId, int buyerUserId) {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("PHYSICAL");
        request.setTitle("二手键盘");
        request.setDescription("九成新");
        request.setUnitPrice(12_900L);
        request.setStockTotal(1);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);
        long listingId = marketListingService.createListing(sellerUserId, request, null).listingId();

        CreateMarketAddressRequest addressRequest = new CreateMarketAddressRequest();
        addressRequest.setReceiverName("张三");
        addressRequest.setReceiverPhone("13800000000");
        addressRequest.setProvince("上海市");
        addressRequest.setCity("上海市");
        addressRequest.setDistrict("浦东新区");
        addressRequest.setDetailAddress("世纪大道 100 号");
        addressRequest.setPostalCode("200120");
        addressRequest.setDefault(true);
        long addressId = marketAddressService.createAddress(buyerUserId, addressRequest).addressId();

        long orderId = marketOrderService.createOrder("auto-confirm:physical:req-1", buyerUserId, listingId, 1, addressId).orderId();
        marketOrderService.shipPhysicalOrder(orderId, sellerUserId, "顺丰", "SF1234567890", "工作日派送");
        jdbcTemplate.update(
                "update market_order set auto_confirm_at = timestampadd('DAY', -1, current_timestamp) where order_id = ?",
                orderId
        );
        return orderId;
    }

    private void seedBuyerBalance(int userId, long balance) {
        long accountId = walletAccountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "select status from market_order where order_id = ?",
                String.class,
                orderId
        );
    }
}
