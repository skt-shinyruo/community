package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.market.controller.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.application.result.MarketDisputeResult;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
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
        assertThat(resolved.sellerNote()).isEqualTo("证据支持卖家");
        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("DISPUTE_RELEASE_PENDING");
        marketWalletActionProcessor.processDue(10);

        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("COMPLETED");
        assertThat(walletAccountService.balanceOfUser(sellerUserId)).isEqualTo(12_900L);
    }

    @Test
    void adminResolveRefundWithBlankNoteShouldKeepSellerNote() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID adminUserId = uuid(99);
        seedBuyerBalance(buyerUserId, 20_000L);
        UUID orderId = seedShippedPhysicalOrder(sellerUserId, buyerUserId);

        MarketDisputeResult dispute = marketDisputeService.openDispute(orderId, buyerUserId, "货不对板", "和描述不一致");
        marketDisputeService.sellerRejectRefund(dispute.disputeId(), sellerUserId, "不同意退款");
        MarketDisputeResult resolved = marketDisputeService.adminResolveRefund(dispute.disputeId(), adminUserId, " ");

        assertThat(resolved.status()).isEqualTo("ADMIN_RESOLVED");
        assertThat(resolved.resolutionType()).isEqualTo("REFUND");
        assertThat(resolved.sellerNote()).isEqualTo("不同意退款");
    }

    @Test
    void listOpenDisputesShouldExposeOrderTotalAmount() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        seedBuyerBalance(buyerUserId, 20_000L);
        UUID orderId = seedShippedPhysicalOrder(sellerUserId, buyerUserId);

        MarketDisputeResult dispute = marketDisputeService.openDispute(orderId, buyerUserId, "货不对板", "和描述不一致");
        List<MarketDisputeResult> openDisputes = marketDisputeService.listOpenDisputes();

        assertThat(openDisputes).hasSize(1);
        assertThat(openDisputes.get(0).disputeId()).isEqualTo(dispute.disputeId());
        assertThat(openDisputes.get(0).totalAmount()).isEqualTo(12_900L);
    }

    private UUID seedShippedPhysicalOrder(UUID sellerUserId, UUID buyerUserId) {
        CreateMarketListingRequest request = new CreateMarketListingRequest(
                "PHYSICAL", "二手键盘", "九成新", 12_900L, null, null, 1, 1, 1, null);
        UUID listingId = marketListingService.createListing(MarketTestCommands.listingCommand(sellerUserId, request, null)).listingId();

        CreateMarketAddressRequest addressRequest = new CreateMarketAddressRequest(
                "张三", "13800000000", "上海市", "上海市", "浦东新区", "世纪大道 100 号", "200120", true);
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
