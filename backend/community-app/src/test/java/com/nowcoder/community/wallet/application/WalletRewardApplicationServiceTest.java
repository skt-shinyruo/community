package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.wallet.application.command.WalletRewardCommand;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WalletRewardApplicationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletRewardApplicationService service;

    @Autowired
    private WalletAccountApplicationService accountService;

    @Autowired
    private WalletTransferApplicationService transferService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @MockBean
    private UserLookupQueryApi userLookupQueryApi;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_account");
        when(userLookupQueryApi.getSummaryById(any(UUID.class)))
                .thenAnswer(invocation -> summary(invocation.getArgument(0)));
    }

    @Test
    void issueShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.issue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void revokeShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.revoke(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void applyDeltaShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.applyDelta(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void revokeShouldCreateDebtThenNormalIssueShouldPartiallyRepayItAndTransferRemainNormal() {
        UUID userId = uuid(101);
        UUID recipientUserId = uuid(202);

        service.revoke(new WalletRewardCommand("reward:revoke:debt", userId, 5L, "TEST"));
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(-5L);

        service.issue(new WalletRewardCommand("reward:issue:repay", userId, 3L, "TEST"));
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(-2L);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
        assertThat(txnEntryImbalances()).containsOnly(0L);

        assertThatThrownBy(() -> transferService.create(
                "transfer:debt:blocked",
                userId,
                recipientUserId,
                1L
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT));

        assertThat(accountService.balanceOfUser(userId)).isEqualTo(-2L);
        assertThat(accountService.balanceOfUser(recipientUserId)).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
        assertThat(countRows("transfer_order")).isZero();
    }

    @Test
    void negativeDeltaShouldUsePrivilegedCorrection() {
        UUID userId = uuid(101);

        service.applyDelta(new WalletRewardCommand("reward:delta:negative", userId, -2L, "TEST"));

        assertThat(accountService.balanceOfUser(userId)).isEqualTo(-2L);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
        assertThat(txnEntryImbalances()).containsOnly(0L);
    }

    @Test
    void rewardCommandShouldNotExposePostingPolicy() {
        assertThat(Arrays.stream(WalletRewardCommand.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("requestId", "userId", "amount", "sourceType");
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private java.util.List<Long> txnEntryImbalances() {
        return jdbcTemplate.query(
                "select sum(case when direction = 'DEBIT' then amount else -amount end) as imbalance "
                        + "from wallet_entry group by txn_id",
                (rs, rowNum) -> rs.getLong("imbalance")
        );
    }

    private UserSummaryView summary(UUID userId) {
        return new UserSummaryView(userId, "user-" + userId, null, 0);
    }
}
