package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.controller.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.application.result.MarketDisputeResult;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService;
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
class MarketDisputeApplicationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketListingApplicationService marketListingService;

    @Autowired
    private MarketAddressApplicationService marketAddressService;

    @Autowired
    private MarketOrderApplicationService marketOrderService;

    @Autowired
    private MarketDisputeApplicationService marketDisputeService;

    @Autowired
    private MarketWalletActionProcessorApplicationService marketWalletActionProcessor;

    @Autowired
    private MarketQueryApplicationService marketQueryService;

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
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from wallet_admin_action");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void sellerAcceptedPhysicalDisputeShouldRefundBuyer() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        seedBuyerBalance(buyerUserId, 20_000L);
        UUID orderId = seedShippedPhysicalOrder(sellerUserId, buyerUserId);

        MarketDisputeResult dispute = marketDisputeService.openDispute(orderId, buyerUserId, "货不对板", "和描述不一致");
        MarketDisputeResult resolved = marketDisputeService.sellerAcceptRefund(dispute.disputeId(), sellerUserId, "同意退款");

        assertThat(resolved.status()).isEqualTo("SELLER_ACCEPTED");
        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("DISPUTE_REFUND_PENDING");
        marketWalletActionProcessor.processDue(10);

        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("REFUNDED");
        assertThat(walletAccountService.balanceOfUser(buyerUserId)).isEqualTo(20_000L);
    }

    @Test
    void adminResolveReleaseShouldRemainPendingUntilReleaseProcessorSucceeds() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID adminUserId = uuid(99);
        seedBuyerBalance(buyerUserId, 20_000L);
        UUID orderId = seedShippedPhysicalOrder(sellerUserId, buyerUserId);

        MarketDisputeResult dispute = marketDisputeService.openDispute(orderId, buyerUserId, "货不对板", "和描述不一致");
        MarketDisputeResult resolved = marketDisputeService.adminResolveRelease(dispute.disputeId(), adminUserId, "证据支持卖家");

        assertThat(resolved.status()).isEqualTo("ADMIN_RESOLVED");
        assertThat(resolved.resolutionType()).isEqualTo("RELEASE");
        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("DISPUTE_RELEASE_PENDING");
        marketWalletActionProcessor.processDue(10);

        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("COMPLETED");
        assertThat(walletAccountService.balanceOfUser(sellerUserId)).isEqualTo(12_900L);
    }

    private UUID seedShippedPhysicalOrder(UUID sellerUserId, UUID buyerUserId) {
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
        addressRequest.setDefault(true);
        UUID addressId = marketAddressService.createAddress(MarketTestCommands.addressCommand(buyerUserId, addressRequest)).addressId();

        UUID orderId = marketOrderService.createOrder("dispute:physical:req-1", buyerUserId, listingId, 1, addressId).orderId();
        marketWalletActionProcessor.processDue(10);
        return marketOrderService.shipPhysicalOrder(orderId, sellerUserId, "顺丰", "SF1234567890", "工作日派送").orderId();
    }

    private void seedBuyerBalance(UUID userId, long balance) {
        UUID accountId = walletAccountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }
}
