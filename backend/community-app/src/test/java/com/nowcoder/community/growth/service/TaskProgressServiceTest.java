package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class TaskProgressServiceTest {

    private static final UUID USER_ID = uuid(1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskProgressService service;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_task_event_log");
        jdbcTemplate.update("delete from user_task_progress");
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_grant_record");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from user_score_log");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from wallet_account");
        jdbcTemplate.update("update user set score = 0");
    }

    @Test
    void dailyTaskProgressShouldBeUniqueByUserTaskAndBusinessDate() {
        service.processEvent(USER_ID, "DailyCheckIn", "check-evt-1", LocalDate.of(2026, 3, 22));
        service.processEvent(USER_ID, "DailyCheckIn", "check-evt-2", LocalDate.of(2026, 3, 22));

        assertThat(countProgressRows("DAILY_CHECK_IN")).isEqualTo(1);
        assertThat(progressValue("DAILY_CHECK_IN")).isEqualTo(1);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":DAILY_CHECK_IN:2026-03-22")).isEqualTo(1);
    }

    @Test
    void weeklyTaskProgressShouldBeKeyedByWeek() {
        service.processEvent(USER_ID, "CommentCreated", "comment-evt-1", LocalDate.of(2026, 3, 16));
        service.processEvent(USER_ID, "CommentCreated", "comment-evt-2", LocalDate.of(2026, 3, 17));

        assertThat(progressPeriodKey("WEEKLY_COMMENTER")).isEqualTo("2026-W12");
        assertThat(progressValue("WEEKLY_COMMENTER")).isEqualTo(2);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":WEEKLY_COMMENTER:2026-W12")).isEqualTo(1);
    }

    @Test
    void lifetimeTaskShouldUseStablePeriodKeyAndGrantOnlyOnce() {
        service.processEvent(USER_ID, "LikeCreated", "like-evt-1", LocalDate.of(2026, 3, 20));
        service.processEvent(USER_ID, "LikeCreated", "like-evt-2", LocalDate.of(2026, 3, 21));
        service.processEvent(USER_ID, "LikeCreated", "like-evt-3", LocalDate.of(2026, 3, 22));
        service.processEvent(USER_ID, "LikeCreated", "like-evt-4", LocalDate.of(2026, 3, 23));

        assertThat(progressPeriodKey("LIFETIME_RECEIVE_LIKE")).isEqualTo("LIFETIME");
        assertThat(progressValue("LIFETIME_RECEIVE_LIKE")).isEqualTo(3);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":LIFETIME_RECEIVE_LIKE:LIFETIME")).isEqualTo(1);
    }

    @Test
    void duplicateSourceEventShouldNotDoubleCountProgress() {
        service.processEvent(USER_ID, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));
        service.processEvent(USER_ID, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));

        assertThat(countProgressRows("DAILY_POST")).isEqualTo(1);
        assertThat(progressValue("DAILY_POST")).isEqualTo(1);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":DAILY_POST:2026-03-22")).isEqualTo(1);
    }

    @Test
    void nonAdjacentDuplicateSourceEventShouldNotAdvanceProgressAgain() {
        service.processEvent(USER_ID, "CommentCreated", "comment-evt-1", LocalDate.of(2026, 3, 16));
        service.processEvent(USER_ID, "CommentCreated", "comment-evt-2", LocalDate.of(2026, 3, 17));
        service.processEvent(USER_ID, "CommentCreated", "comment-evt-1", LocalDate.of(2026, 3, 18));

        assertThat(progressValue("WEEKLY_COMMENTER")).isEqualTo(2);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":WEEKLY_COMMENTER:2026-W12")).isEqualTo(1);
    }

    @Test
    void autoGrantShouldInsertRewardOutcomeOnlyOncePerPeriod() {
        service.processEvent(USER_ID, "CommentCreated", "comment-evt-1", LocalDate.of(2026, 3, 16));
        service.processEvent(USER_ID, "CommentCreated", "comment-evt-2", LocalDate.of(2026, 3, 17));
        service.processEvent(USER_ID, "CommentCreated", "comment-evt-3", LocalDate.of(2026, 3, 18));

        assertThat(walletTxnCountFor("task:" + USER_ID + ":WEEKLY_COMMENTER:2026-W12")).isEqualTo(1);
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    private int countProgressRows(String taskCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_task_progress where user_id = ? and task_code = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(USER_ID),
                taskCode
        );
        return count == null ? 0 : count;
    }

    private int progressValue(String taskCode) {
        Integer value = jdbcTemplate.queryForObject(
                "select current_value from user_task_progress where user_id = ? and task_code = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(USER_ID),
                taskCode
        );
        return value == null ? 0 : value;
    }

    private String progressPeriodKey(String taskCode) {
        return jdbcTemplate.queryForObject(
                "select period_key from user_task_progress where user_id = ? and task_code = ?",
                String.class,
                BinaryUuidCodec.toBytes(USER_ID),
                taskCode
        );
    }

    private int walletTxnCountFor(String requestId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where request_id = ?",
                Integer.class,
                requestId
        );
        return count == null ? 0 : count;
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
