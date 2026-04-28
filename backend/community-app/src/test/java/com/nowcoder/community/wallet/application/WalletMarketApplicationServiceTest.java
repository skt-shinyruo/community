package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletTxnMapper;
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
class WalletMarketApplicationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletMarketActionApi walletMarketActionApi;

    @Autowired
    private WalletAccountApplicationService walletAccountService;

    @Autowired
    private WalletTxnMapper walletTxnMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
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
    void escrowReleaseAndRefundShouldPostOrderTransactions() {
        UUID firstBuyerId = uuid(1);
        UUID sellerUserId = uuid(2);
        UUID secondBuyerId = uuid(3);
        seedUserBalance(firstBuyerId, 5_000L);

        WalletMarketTxnView escrow = walletMarketActionApi.escrowOrder("order:1:escrow", firstBuyerId, 2_000L, "virtual-order:1");

        assertThat(escrow.txnType()).isEqualTo("ORDER_ESCROW");
        assertThat(escrow.status()).isEqualTo("SUCCEEDED");
        assertThat(escrow.txnId()).isNotNull();
        assertThat(escrow.txnId().version()).isEqualTo(7);
        assertThat(escrow.amount()).isEqualTo(2_000L);
        assertThat(escrow.bizId()).isEqualTo("virtual-order:1");
        assertThat(walletAccountService.balanceOfUser(firstBuyerId)).isEqualTo(3_000L);
        assertThat(walletAccountService.balanceOfSystem("ORDER_ESCROW")).isEqualTo(2_000L);
        assertThat(walletTxnMapper.selectByRequestId("order:1:escrow").getTxnType()).isEqualTo("ORDER_ESCROW");
        assertThat(walletTxnMapper.selectByRequestId("order:1:escrow").getBizId()).isEqualTo("virtual-order:1");

        WalletMarketTxnView release = walletMarketActionApi.releaseOrder("order:1:release", sellerUserId, 2_000L, "virtual-order:1");

        assertThat(release.txnType()).isEqualTo("ORDER_RELEASE");
        assertThat(walletAccountService.balanceOfUser(sellerUserId)).isEqualTo(2_000L);
        assertThat(walletAccountService.balanceOfSystem("ORDER_ESCROW")).isEqualTo(0L);
        assertThat(walletTxnMapper.selectByRequestId("order:1:release").getTxnType()).isEqualTo("ORDER_RELEASE");
        assertThat(walletTxnMapper.selectByRequestId("order:1:release").getBizId()).isEqualTo("virtual-order:1");

        seedUserBalance(secondBuyerId, 4_000L);
        walletMarketActionApi.escrowOrder("order:2:escrow", secondBuyerId, 1_500L, "virtual-order:2");

        WalletMarketTxnView refund = walletMarketActionApi.refundOrder("order:2:refund", secondBuyerId, 1_500L, "virtual-order:2");

        assertThat(refund.txnType()).isEqualTo("ORDER_REFUND");
        assertThat(walletAccountService.balanceOfUser(secondBuyerId)).isEqualTo(4_000L);
        assertThat(walletAccountService.balanceOfSystem("ORDER_ESCROW")).isEqualTo(0L);
        assertThat(walletTxnMapper.selectByRequestId("order:2:refund").getTxnType()).isEqualTo("ORDER_REFUND");
        assertThat(walletTxnMapper.selectByRequestId("order:2:refund").getBizId()).isEqualTo("virtual-order:2");
    }

    private void seedUserBalance(UUID userId, long balance) {
        UUID accountId = walletAccountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }
}
