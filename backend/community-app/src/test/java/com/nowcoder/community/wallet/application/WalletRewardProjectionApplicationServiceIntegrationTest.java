package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.application.command.RewardProjectionCommand;
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
class WalletRewardProjectionApplicationServiceIntegrationTest {

    private static final UUID USER_ID = uuid(1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletRewardProjectionApplicationService walletRewardProjectionApplicationService;

    @Autowired
    private WalletAccountApplicationService walletAccountService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from wallet_account");
        jdbcTemplate.update(
                "merge into user (id, username, password, salt, email, type, status, header_url, create_time) key(id) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(USER_ID),
                "u1",
                "p",
                "s",
                "u1@example.com",
                0,
                1,
                "http://old.local/a.png"
        );
    }

    @Test
    void postPublishedShouldCreditWallet() {
        walletRewardProjectionApplicationService.apply(new RewardProjectionCommand(
                USER_ID,
                10,
                "post-reward-1",
                "PostPublished"
        ));

        assertThat(walletAccountService.balanceOfUser(USER_ID)).isEqualTo(10);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void duplicateLikeRewardShouldCreateOneWalletTxnThroughStableIdempotencyKey() {
        String sourceEventId = "like:" + uuid(2) + ":1:" + uuid(3) + ":created";
        RewardProjectionCommand command =
                walletRewardProjectionApplicationService.commandForLikeCreated(
                sourceEventId,
                uuid(2),
                USER_ID
        );

        walletRewardProjectionApplicationService.apply(command);
        walletRewardProjectionApplicationService.apply(command);

        assertThat(walletAccountService.balanceOfUser(USER_ID)).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
        assertThat(countRowsByRequestId("wallet-reward:" + sourceEventId)).isEqualTo(1);
    }

    @Test
    void replayedOutOfOrderLikeEventsShouldPersistEachLifecycleActionOnce() {
        UUID actorUserId = uuid(2);
        String firstLifecycle = uuid(501).toString();
        String secondLifecycle = uuid(502).toString();
        RewardProjectionCommand firstRemoved = walletRewardProjectionApplicationService.commandForLikeRemoved(
                firstLifecycle + ":removed", actorUserId, USER_ID);
        RewardProjectionCommand firstCreated = walletRewardProjectionApplicationService.commandForLikeCreated(
                firstLifecycle + ":created", actorUserId, USER_ID);
        RewardProjectionCommand secondCreated = walletRewardProjectionApplicationService.commandForLikeCreated(
                secondLifecycle + ":created", actorUserId, USER_ID);
        RewardProjectionCommand secondRemoved = walletRewardProjectionApplicationService.commandForLikeRemoved(
                secondLifecycle + ":removed", actorUserId, USER_ID);

        applyTwice(firstRemoved);
        applyTwice(firstCreated);
        applyTwice(secondCreated);
        applyTwice(secondRemoved);

        assertThat(walletAccountService.balanceOfUser(USER_ID)).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(4);
        assertThat(countRows("wallet_entry")).isEqualTo(8);
        assertThat(countRowsByRequestId("wallet-reward:" + firstLifecycle + ":removed")).isEqualTo(1);
        assertThat(countRowsByRequestId("wallet-reward:" + firstLifecycle + ":created")).isEqualTo(1);
        assertThat(countRowsByRequestId("wallet-reward:" + secondLifecycle + ":created")).isEqualTo(1);
        assertThat(countRowsByRequestId("wallet-reward:" + secondLifecycle + ":removed")).isEqualTo(1);
    }

    private void applyTwice(RewardProjectionCommand command) {
        walletRewardProjectionApplicationService.apply(command);
        walletRewardProjectionApplicationService.apply(command);
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private int countRowsByRequestId(String requestId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where request_id = ?",
                Integer.class,
                requestId
        );
        return count == null ? 0 : count;
    }
}
