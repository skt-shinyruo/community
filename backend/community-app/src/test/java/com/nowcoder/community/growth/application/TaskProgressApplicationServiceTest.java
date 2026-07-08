package com.nowcoder.community.growth.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeRemovedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class TaskProgressApplicationServiceTest {

    private static final UUID USER_ID = uuid(1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskProgressApplicationService service;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_task_event_log");
        jdbcTemplate.update("delete from user_task_progress");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from wallet_account");
        jdbcTemplate.update("delete from task_template where task_code like 'TEST_%'");
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
    void triggerPostPublishedShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.triggerPostPublished(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void triggerCommentCreatedShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.triggerCommentCreated(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void triggerLikeCreatedShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.triggerLikeCreated(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void triggerLikeRemovedShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.triggerLikeRemoved(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
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
    void likeRemovedShouldRollbackClaimableLikeTaskProgress() {
        upsertLikeTaskTemplate("TEST_DAILY_RECEIVE_LIKE_CLAIM", "DAILY", 1, true);
        Instant createTime = Instant.parse("2026-03-22T10:30:00Z");
        String relationKey = "like:" + uuid(9) + ":3:" + uuid(100);

        service.triggerLikeCreated(new TriggerLikeCreatedCommand(relationKey, uuid(9), USER_ID, createTime));

        assertThat(progressValue("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isEqualTo(1);
        assertThat(progressStatus("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isEqualTo("CLAIMABLE");
        assertThat(eventLogCount("TEST_DAILY_RECEIVE_LIKE_CLAIM", relationKey)).isEqualTo(1);

        service.triggerLikeRemoved(new TriggerLikeRemovedCommand(relationKey, USER_ID));

        assertThat(progressValue("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isZero();
        assertThat(progressStatus("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isEqualTo("IN_PROGRESS");
        assertThat(progressReachedAt("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isNull();
        assertThat(eventLogCount("TEST_DAILY_RECEIVE_LIKE_CLAIM", relationKey)).isZero();
    }

    @Test
    void likeRemovedShouldNotRollbackClaimedLikeTaskProgress() {
        String firstRelationKey = "like:" + uuid(9) + ":3:" + uuid(100);
        String secondRelationKey = "like:" + uuid(9) + ":3:" + uuid(101);
        String thirdRelationKey = "like:" + uuid(9) + ":3:" + uuid(102);

        service.triggerLikeCreated(new TriggerLikeCreatedCommand(firstRelationKey, uuid(9), USER_ID, Instant.parse("2026-03-20T10:30:00Z")));
        service.triggerLikeCreated(new TriggerLikeCreatedCommand(secondRelationKey, uuid(9), USER_ID, Instant.parse("2026-03-21T10:30:00Z")));
        service.triggerLikeCreated(new TriggerLikeCreatedCommand(thirdRelationKey, uuid(9), USER_ID, Instant.parse("2026-03-22T10:30:00Z")));

        assertThat(progressStatus("LIFETIME_RECEIVE_LIKE")).isEqualTo("CLAIMED");
        assertThat(progressValue("LIFETIME_RECEIVE_LIKE")).isEqualTo(3);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":LIFETIME_RECEIVE_LIKE:LIFETIME")).isEqualTo(1);
        assertThat(eventLogCount("LIFETIME_RECEIVE_LIKE", firstRelationKey)).isEqualTo(1);

        service.triggerLikeRemoved(new TriggerLikeRemovedCommand(firstRelationKey, USER_ID));

        assertThat(progressStatus("LIFETIME_RECEIVE_LIKE")).isEqualTo("CLAIMED");
        assertThat(progressValue("LIFETIME_RECEIVE_LIKE")).isEqualTo(3);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":LIFETIME_RECEIVE_LIKE:LIFETIME")).isEqualTo(1);
        assertThat(eventLogCount("LIFETIME_RECEIVE_LIKE", firstRelationKey)).isEqualTo(1);
    }

    @Test
    void replayedSourceEventShouldNotIncrementTaskTwice() {
        service.processEvent(USER_ID, "PostPublished", "post-evt-replayed", LocalDate.of(2026, 3, 22));
        service.processEvent(USER_ID, "PostPublished", "post-evt-replayed", LocalDate.of(2026, 3, 22));

        assertThat(countProgressRows("DAILY_POST")).isEqualTo(1);
        assertThat(progressValue("DAILY_POST")).isEqualTo(1);
        assertThat(eventLogCount("DAILY_POST", "post-evt-replayed")).isEqualTo(1);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":DAILY_POST:2026-03-22")).isEqualTo(1);
    }

    @Test
    void replayedLikeCreatedSourceEventShouldNotIncrementTaskTwice() {
        service.processEvent(USER_ID, "LikeCreated", "like-evt-replayed", LocalDate.of(2026, 3, 22));
        service.processEvent(USER_ID, "LikeCreated", "like-evt-replayed", LocalDate.of(2026, 3, 22));

        assertThat(countProgressRows("LIFETIME_RECEIVE_LIKE")).isEqualTo(1);
        assertThat(progressValue("LIFETIME_RECEIVE_LIKE")).isEqualTo(1);
        assertThat(eventLogCount("LIFETIME_RECEIVE_LIKE", "like-evt-replayed")).isEqualTo(1);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":LIFETIME_RECEIVE_LIKE:LIFETIME")).isZero();
    }

    @Test
    void walletRewardShouldUseBalanceDeltaOnly() {
        String requestId = "task:" + USER_ID + ":DAILY_POST:2026-03-22";

        service.processEvent(USER_ID, "PostPublished", "post-evt-balance-only", LocalDate.of(2026, 3, 22));

        assertThat(walletTxnAmountFor(requestId)).isEqualTo(1L);
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

    private String progressStatus(String taskCode) {
        return jdbcTemplate.queryForObject(
                "select status from user_task_progress where user_id = ? and task_code = ?",
                String.class,
                BinaryUuidCodec.toBytes(USER_ID),
                taskCode
        );
    }

    private Timestamp progressReachedAt(String taskCode) {
        return jdbcTemplate.queryForObject(
                "select reached_at from user_task_progress where user_id = ? and task_code = ?",
                Timestamp.class,
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

    private long walletTxnAmountFor(String requestId) {
        Long amount = jdbcTemplate.queryForObject(
                "select amount from wallet_txn where request_id = ?",
                Long.class,
                requestId
        );
        return amount == null ? 0L : amount;
    }

    private int eventLogCount(String taskCode, String sourceEventId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_task_event_log where user_id = ? and task_code = ? and source_event_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(USER_ID),
                taskCode,
                sourceEventId
        );
        return count == null ? 0 : count;
    }

    private void upsertLikeTaskTemplate(String taskCode, String periodType, int targetValue, boolean claimRequired) {
        jdbcTemplate.update(
                "merge into task_template(task_code, task_type, period_type, trigger_event_type, target_value, reward_growth_delta, reward_balance_delta, claim_required, display_order, status) key(task_code) values (?, 'SOCIAL', ?, 'LikeCreated', ?, 0, 0, ?, 99, 'ACTIVE')",
                taskCode,
                periodType,
                targetValue,
                claimRequired
        );
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
