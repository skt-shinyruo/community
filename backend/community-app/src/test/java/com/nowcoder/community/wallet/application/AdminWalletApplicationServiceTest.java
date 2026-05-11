package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.domain.model.WalletTxn;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletTxnMapper;
import com.nowcoder.community.wallet.application.result.RechargeOrderResult;
import com.nowcoder.community.wallet.application.result.TransferOrderResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class AdminWalletApplicationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletAdminOpsApplicationService adminWalletOpsService;

    @Autowired
    private WalletAccountApplicationService accountService;

    @Autowired
    private WalletLedgerApplicationService ledgerService;

    @Autowired
    private WalletTransferApplicationService transferService;

    @Autowired
    private WalletWithdrawApplicationService withdrawService;

    @Autowired
    private WalletRechargeApplicationService rechargeService;

    @Autowired
    private WalletTxnMapper walletTxnMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_admin_action");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void freezeShouldBlockTransferAndWithdraw() {
        UUID actorUserId = uuid(1);
        UUID sourceUserId = uuid(101);
        UUID targetUserId = uuid(202);
        seedUserBalance(sourceUserId, 500);
        seedSystemBalance("PLATFORM_CASH", 800);

        adminWalletOpsService.freezeWallet(actorUserId, sourceUserId, "risk review");

        assertThatThrownBy(() -> transferService.create("transfer:req-2", sourceUserId, targetUserId, 100))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_FROZEN))
                .hasMessageContaining("frozen");
        assertThatThrownBy(() -> withdrawService.request("withdraw:req-2", sourceUserId, 100))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_FROZEN))
                .hasMessageContaining("frozen");

        assertThat(countRows("transfer_order")).isZero();
        assertThat(countRows("withdraw_order")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select status from wallet_account where owner_type = 'USER' and owner_id = ? and account_type = 'USER_WALLET'",
                String.class,
                BinaryUuidCodec.toBytes(sourceUserId)
        )).isEqualTo("FROZEN");
        assertThat(countRows("wallet_admin_action")).isEqualTo(1);
        byte[] actionId = jdbcTemplate.queryForObject(
                "select action_id from wallet_admin_action where request_id like 'wallet-admin:freeze:%'",
                (rs, rowNum) -> rs.getBytes(1)
        );
        assertThat(actionId).hasSize(16);
        UUID parsedActionId = BinaryUuidCodec.fromBytes(actionId);
        assertThat(parsedActionId.version()).isEqualTo(7);
    }

    @Test
    void reverseTransferShouldCreateReversalTxnInsteadOfEditingBalancesInPlace() {
        UUID actorUserId = uuid(1);
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        seedUserBalance(fromUserId, 900);
        TransferOrderResult order = transferService.create("transfer:req-1", fromUserId, toUserId, 300);
        String txnRef = "wallet:transfer:" + order.orderId();

        adminWalletOpsService.reverseTxn(actorUserId, txnRef, "fraud report");

        assertThat(accountService.balanceOfUser(fromUserId)).isEqualTo(900);
        assertThat(accountService.balanceOfUser(toUserId)).isEqualTo(0);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
        assertThat(countRows("wallet_admin_action")).isEqualTo(1);

        WalletTxn original = walletTxnMapper.selectByRequestId(txnRef);
        WalletTxn reversal = walletTxnMapper.selectByRequestId("reversal:" + txnRef);
        assertThat(original).isNotNull();
        assertThat(reversal).isNotNull();
        assertThat(reversal.getTxnType()).isEqualTo("REVERSAL");
        byte[] targetTxnId = jdbcTemplate.queryForObject(
                "select target_account_id from wallet_admin_action",
                (rs, rowNum) -> rs.getBytes(1)
        );
        assertThat(BinaryUuidCodec.fromBytes(targetTxnId)).isEqualTo(original.getTxnId());
        assertThat(jdbcTemplate.queryForObject("select request_id from wallet_admin_action", String.class))
                .isEqualTo("wallet-admin:reverse:" + txnRef);
        assertThat(ledgerService.entriesOfTxn(original.getTxnId()))
                .extracting(entry -> entry.getDirection() + ":" + entry.getAmount())
                .containsExactly("DEBIT:300", "CREDIT:300");
        assertThat(ledgerService.entriesOfTxn(reversal.getTxnId()))
                .extracting(entry -> entry.getDirection() + ":" + entry.getAmount())
                .containsExactly("CREDIT:300", "DEBIT:300");
    }

    @Test
    void reverseShouldRejectWhenRecipientAlreadySpentFunds() {
        UUID actorUserId = uuid(1);
        UUID originUserId = uuid(101);
        UUID intermediateUserId = uuid(202);
        UUID downstreamUserId = uuid(303);
        seedUserBalance(originUserId, 900);
        TransferOrderResult originOrder = transferService.create("transfer:req-spent-origin", originUserId, intermediateUserId, 300);
        transferService.create("transfer:req-spent-downstream", intermediateUserId, downstreamUserId, 300);
        String txnRef = "wallet:transfer:" + originOrder.orderId();

        assertThatThrownBy(() -> adminWalletOpsService.reverseTxn(actorUserId, txnRef, "fraud report"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT))
                .hasMessageContaining("reversal")
                .hasMessageContaining(txnRef);

        assertThat(accountService.balanceOfUser(originUserId)).isEqualTo(600);
        assertThat(accountService.balanceOfUser(intermediateUserId)).isEqualTo(0);
        assertThat(accountService.balanceOfUser(downstreamUserId)).isEqualTo(300);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
        assertThat(countRows("wallet_admin_action")).isZero();
    }

    @Test
    void repeatReverseShouldKeepOneLogicalAdminAction() {
        UUID actorUserId = uuid(1);
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        seedUserBalance(fromUserId, 900);
        TransferOrderResult order = transferService.create("transfer:req-repeat", fromUserId, toUserId, 300);
        String txnRef = "wallet:transfer:" + order.orderId();

        adminWalletOpsService.reverseTxn(actorUserId, txnRef, "fraud report");
        adminWalletOpsService.reverseTxn(actorUserId, txnRef, "fraud report");

        WalletTxn original = walletTxnMapper.selectByRequestId(txnRef);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
        assertThat(countRows("wallet_admin_action")).isEqualTo(1);
        byte[] targetTxnId = jdbcTemplate.queryForObject(
                "select target_account_id from wallet_admin_action",
                (rs, rowNum) -> rs.getBytes(1)
        );
        assertThat(BinaryUuidCodec.fromBytes(targetTxnId)).isEqualTo(original.getTxnId());
        assertThat(jdbcTemplate.queryForObject("select request_id from wallet_admin_action", String.class))
                .isEqualTo("wallet-admin:reverse:" + txnRef);
    }

    @Test
    void reverseShouldRejectUnsupportedTxnType() {
        UUID actorUserId = uuid(1);
        UUID userId = uuid(101);
        seedSystemBalance("PLATFORM_CASH", 900);
        RechargeOrderResult order = rechargeService.complete("recharge:req-unsupported", userId, 200);

        assertThatThrownBy(() -> adminWalletOpsService.reverseTxn(actorUserId, "wallet:recharge:" + order.orderId(), "fraud report"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST))
                .hasMessageContaining("not reversible");

        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
        assertThat(countRows("wallet_admin_action")).isZero();
        assertThat(walletTxnMapper.selectByRequestId("wallet:recharge:" + order.orderId())).isNotNull();
    }

    @Test
    void reverseShouldRejectBusinessOrderRequestId() {
        UUID actorUserId = uuid(1);
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        seedUserBalance(fromUserId, 900);
        TransferOrderResult order = transferService.create("transfer:req-canonical-only", fromUserId, toUserId, 300);

        assertThatThrownBy(() -> adminWalletOpsService.reverseTxn(actorUserId, "transfer:req-canonical-only", "fraud report"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST))
                .hasMessageContaining("wallet txn not found");

        assertThat(walletTxnMapper.selectByRequestId("wallet:transfer:" + order.orderId())).isNotNull();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_admin_action")).isZero();
    }

    private void seedUserBalance(UUID userId, long balance) {
        UUID accountId = accountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }

    private void seedSystemBalance(String accountType, long balance) {
        UUID accountId = accountService.ensureSystemAccount(accountType);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
