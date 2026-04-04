package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.dto.MarketDisputeResponse;
import com.nowcoder.community.wallet.service.WalletAccountService;
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
class MarketDisputeServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketListingService marketListingService;

    @Autowired
    private MarketAddressService marketAddressService;

    @Autowired
    private MarketOrderService marketOrderService;

    @Autowired
    private MarketDisputeService marketDisputeService;

    @Autowired
    private MarketQueryService marketQueryService;

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
    }

    @Test
    void sellerAcceptedPhysicalDisputeShouldRefundBuyer() {
        seedBuyerBalance(9, 20_000L);
        long orderId = seedShippedPhysicalOrder(7, 9);

        MarketDisputeResponse dispute = marketDisputeService.openDispute(orderId, 9, "货不对板", "和描述不一致");
        MarketDisputeResponse resolved = marketDisputeService.sellerAcceptRefund(dispute.disputeId(), 7, "同意退款");

        assertThat(resolved.status()).isEqualTo("SELLER_ACCEPTED");
        assertThat(marketQueryService.getOrderDetail(orderId, 9).status()).isEqualTo("REFUNDED");
        assertThat(walletAccountService.balanceOfUser(9)).isEqualTo(20_000L);
    }

    private long seedShippedPhysicalOrder(int sellerUserId, int buyerUserId) {
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

        long orderId = marketOrderService.createOrder("dispute:physical:req-1", buyerUserId, listingId, 1, addressId).orderId();
        return marketOrderService.shipPhysicalOrder(orderId, sellerUserId, "顺丰", "SF1234567890", "工作日派送").orderId();
    }

    private void seedBuyerBalance(int userId, long balance) {
        long accountId = walletAccountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }
}
