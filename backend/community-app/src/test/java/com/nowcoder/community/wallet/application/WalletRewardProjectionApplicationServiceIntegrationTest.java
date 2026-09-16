package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
        UUID postId = uuid(401);

        walletRewardProjectionApplicationService.postPublished(postId, USER_ID);

        assertThat(walletAccountService.balanceOfUser(USER_ID)).isEqualTo(10);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
        assertThat(countRowsByRequestId("wallet-reward:post-published:" + postId)).isEqualTo(1);
    }

    @Test
    void duplicateLikeRewardShouldCreateOneWalletTxnThroughStableIdempotencyKey() {
        String sourceEventId = "like:" + uuid(2) + ":1:" + uuid(3) + ":created";

        walletRewardProjectionApplicationService.likeCreated(sourceEventId, uuid(2), USER_ID);
        walletRewardProjectionApplicationService.likeCreated(sourceEventId, uuid(2), USER_ID);

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

        likeRemovedTwice(firstLifecycle + ":removed", actorUserId);
        likeCreatedTwice(firstLifecycle + ":created", actorUserId);
        likeCreatedTwice(secondLifecycle + ":created", actorUserId);
        likeRemovedTwice(secondLifecycle + ":removed", actorUserId);

        assertThat(walletAccountService.balanceOfUser(USER_ID)).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(4);
        assertThat(countRows("wallet_entry")).isEqualTo(8);
        assertThat(countRowsByRequestId("wallet-reward:" + firstLifecycle + ":removed")).isEqualTo(1);
        assertThat(countRowsByRequestId("wallet-reward:" + firstLifecycle + ":created")).isEqualTo(1);
        assertThat(countRowsByRequestId("wallet-reward:" + secondLifecycle + ":created")).isEqualTo(1);
        assertThat(countRowsByRequestId("wallet-reward:" + secondLifecycle + ":removed")).isEqualTo(1);
    }

    private void likeCreatedTwice(String sourceEventId, UUID actorUserId) {
        walletRewardProjectionApplicationService.likeCreated(sourceEventId, actorUserId, USER_ID);
        walletRewardProjectionApplicationService.likeCreated(sourceEventId, actorUserId, USER_ID);
    }

    private void likeRemovedTwice(String sourceEventId, UUID actorUserId) {
        walletRewardProjectionApplicationService.likeRemoved(sourceEventId, actorUserId, USER_ID);
        walletRewardProjectionApplicationService.likeRemoved(sourceEventId, actorUserId, USER_ID);
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
