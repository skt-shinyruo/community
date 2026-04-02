package com.nowcoder.community.wallet.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.dto.CreateRechargeResponse;
import com.nowcoder.community.wallet.entity.RechargeOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.RechargeOrderMapper;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class RechargeServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private WalletAccountService accountService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void completeRechargeShouldCreditUserWalletOnce() {
        CreateRechargeResponse result = rechargeService.complete("recharge:req-1", 101, 1200);

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(accountService.balanceOfUser(101)).isEqualTo(1200);
    }

    @Test
    void completeRechargeShouldReturnExistingOrderForSameRequestIdAndPayload() {
        CreateRechargeResponse first = rechargeService.complete("recharge:req-replay", 101, 1200);

        CreateRechargeResponse second = rechargeService.complete("recharge:req-replay", 101, 1200);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.status()).isEqualTo("PAID");
        assertThat(accountService.balanceOfUser(101)).isEqualTo(1200);
        assertThat(countRows("recharge_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void completeRechargeShouldRejectReplayWhenUserIdDoesNotMatchExistingOrder() {
        rechargeService.complete("recharge:req-user-mismatch", 101, 1200);

        assertThatThrownBy(() -> rechargeService.complete("recharge:req-user-mismatch", 202, 1200))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void completeRechargeShouldRejectReplayWhenAmountDoesNotMatchExistingOrder() {
        rechargeService.complete("recharge:req-amount-mismatch", 101, 1200);

        assertThatThrownBy(() -> rechargeService.complete("recharge:req-amount-mismatch", 101, 1300))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void completeRechargeShouldLoadExistingOrderWhenInsertLosesDuplicateKeyRace() {
        RechargeOrderMapper mapper = mock(RechargeOrderMapper.class);
        WalletAccountService mockedAccountService = mock(WalletAccountService.class);
        WalletLedgerService mockedLedgerService = mock(WalletLedgerService.class);
        RechargeService service = new RechargeService(mapper, mockedAccountService, mockedLedgerService);

        RechargeOrder createdOrder = order(10L, "recharge:req-race", 101, 1200, "CREATED");
        RechargeOrder paidOrder = order(10L, "recharge:req-race", 101, 1200, "PAID");

        when(mapper.selectByRequestId("recharge:req-race"))
                .thenReturn(null, createdOrder, paidOrder);
        when(mockedAccountService.ensureSystemAccount("PLATFORM_CASH")).thenReturn(1L);
        when(mockedAccountService.ensureUserWallet(101)).thenReturn(2L);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate request"))
                .when(mapper).insert(any(RechargeOrder.class));

        CreateRechargeResponse result = service.complete("recharge:req-race", 101, 1200);

        assertThat(result.orderId()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo("PAID");
        verify(mockedLedgerService).post(eq("recharge:req-race"), eq(WalletTxnType.RECHARGE), anyList());
        verify(mapper).updateStatus("recharge:req-race", "CREATED", "PAID");
    }

    private RechargeOrder order(long orderId, String requestId, long userId, long amount, String status) {
        RechargeOrder order = new RechargeOrder();
        order.setOrderId(orderId);
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus(status);
        return order;
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
