package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.growth.entity.RewardLedgerEntry;
import com.nowcoder.community.user.mapper.UserScoreLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class RewardLogMapperPersistenceTest {

    private static final UUID USER_SCORE_LOG_ID = UUID.fromString("00000000-0000-7000-8000-000000000501");
    private static final UUID REWARD_LEDGER_ID_1 = UUID.fromString("00000000-0000-7000-8000-000000000511");
    private static final UUID REWARD_LEDGER_ID_2 = UUID.fromString("00000000-0000-7000-8000-000000000512");
    private static final UUID REWARD_GRANT_RECORD_ID = UUID.fromString("00000000-0000-7000-8000-000000000521");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserScoreLogMapper userScoreLogMapper;

    @Autowired
    private RewardLedgerMapper rewardLedgerMapper;

    @Autowired
    private RewardGrantRecordMapper rewardGrantRecordMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_grant_record");
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from user_score_log");
    }

    @Test
    void userScoreLogInsertShouldPersistApplicationAssignedUuidPrimaryKey() {
        UUID userId = uuid(1);
        int inserted = userScoreLogMapper.insert(
                USER_SCORE_LOG_ID,
                userId,
                "score-evt-1",
                "PostPublished",
                10
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from user_score_log where event_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "score-evt-1"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(USER_SCORE_LOG_ID);
    }

    @Test
    void rewardLedgerInsertAndSelectShouldUseUuidPrimaryKeys() {
        UUID userId = uuid(1);
        rewardLedgerMapper.insert(
                REWARD_LEDGER_ID_1,
                userId,
                "reward-evt-1",
                "RewardGranted",
                3,
                3,
                0,
                "growth",
                "first"
        );
        rewardLedgerMapper.insert(
                REWARD_LEDGER_ID_2,
                userId,
                "reward-evt-2",
                "RewardGranted",
                5,
                8,
                0,
                "growth",
                "second"
        );

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from reward_ledger where event_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "reward-evt-2"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(REWARD_LEDGER_ID_2);

        List<RewardLedgerEntry> entries = rewardLedgerMapper.selectRecentByUserId(userId, 10);

        assertThat(entries).hasSize(2);
        UUID firstId = entries.get(0).getId();
        UUID secondId = entries.get(1).getId();
        assertThat(firstId).isEqualTo(REWARD_LEDGER_ID_2);
        assertThat(secondId).isEqualTo(REWARD_LEDGER_ID_1);
    }

    @Test
    void rewardGrantRecordInsertShouldPersistApplicationAssignedUuidPrimaryKey() {
        int inserted = rewardGrantRecordMapper.insert(
                REWARD_GRANT_RECORD_ID,
                "grant-evt-1",
                uuid(1),
                "DailyTask",
                "task-evt-1",
                "TaskCompleted",
                10,
                5,
                "SUCCEEDED"
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from reward_grant_record where grant_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "grant-evt-1"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(REWARD_GRANT_RECORD_ID);
    }
}
