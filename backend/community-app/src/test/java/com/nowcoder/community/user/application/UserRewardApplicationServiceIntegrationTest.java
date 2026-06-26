package com.nowcoder.community.user.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
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
class UserRewardApplicationServiceIntegrationTest {

    private static final UUID USER_ID = uuid(1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRewardApplicationService userRewardApplicationService;

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
        userRewardApplicationService.apply(new UserRewardApplicationService.RewardCommand(
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
        UserRewardApplicationService.RewardCommand command = userRewardApplicationService.commandForLikeCreated(
                sourceEventId,
                uuid(2),
                USER_ID
        );

        userRewardApplicationService.apply(command);
        userRewardApplicationService.apply(command);

        assertThat(walletAccountService.balanceOfUser(USER_ID)).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
        assertThat(countRowsByRequestId("wallet-reward:" + sourceEventId)).isEqualTo(1);
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
