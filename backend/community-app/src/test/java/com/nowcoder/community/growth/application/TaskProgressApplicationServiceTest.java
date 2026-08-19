package com.nowcoder.community.growth.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerLikeRemovedCommand;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerPostPublishedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from growth_like_task_lifecycle_state");
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
        UUID relationInstanceId = uuid(700);

        triggerLikeCreated("like-created-1", 1L, relationKey, relationInstanceId, uuid(9), createTime);

        assertThat(progressValue("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isEqualTo(1);
        assertThat(progressStatus("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isEqualTo("CLAIMABLE");
        assertThat(eventLogCount("TEST_DAILY_RECEIVE_LIKE_CLAIM", contributionId(relationInstanceId))).isEqualTo(1);

        triggerLikeRemoved("like-removed-1", 2L, relationKey, relationInstanceId);

        assertThat(progressValue("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isZero();
        assertThat(progressStatus("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isEqualTo("IN_PROGRESS");
        assertThat(progressReachedAt("TEST_DAILY_RECEIVE_LIKE_CLAIM")).isNull();
        assertThat(eventLogCount("TEST_DAILY_RECEIVE_LIKE_CLAIM", contributionId(relationInstanceId))).isZero();
    }

    @Test
    void likeRemovedShouldNotRollbackClaimedLikeTaskProgress() {
        String firstRelationKey = "like:" + uuid(9) + ":3:" + uuid(100);
        String secondRelationKey = "like:" + uuid(9) + ":3:" + uuid(101);
        String thirdRelationKey = "like:" + uuid(9) + ":3:" + uuid(102);

        triggerLikeCreated("like-created-1", 1L, firstRelationKey, uuid(701), uuid(9), Instant.parse("2026-03-20T10:30:00Z"));
        triggerLikeCreated("like-created-2", 1L, secondRelationKey, uuid(702), uuid(9), Instant.parse("2026-03-21T10:30:00Z"));
        triggerLikeCreated("like-created-3", 1L, thirdRelationKey, uuid(703), uuid(9), Instant.parse("2026-03-22T10:30:00Z"));

        assertThat(progressStatus("LIFETIME_RECEIVE_LIKE")).isEqualTo("CLAIMED");
        assertThat(progressValue("LIFETIME_RECEIVE_LIKE")).isEqualTo(3);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":LIFETIME_RECEIVE_LIKE:LIFETIME")).isEqualTo(1);
        assertThat(eventLogCount("LIFETIME_RECEIVE_LIKE", contributionId(uuid(701)))).isEqualTo(1);

        triggerLikeRemoved("like-removed-1", 2L, firstRelationKey, uuid(701));

        assertThat(progressStatus("LIFETIME_RECEIVE_LIKE")).isEqualTo("CLAIMED");
        assertThat(progressValue("LIFETIME_RECEIVE_LIKE")).isEqualTo(3);
        assertThat(walletTxnCountFor("task:" + USER_ID + ":LIFETIME_RECEIVE_LIKE:LIFETIME")).isEqualTo(1);
        assertThat(eventLogCount("LIFETIME_RECEIVE_LIKE", contributionId(uuid(701)))).isEqualTo(1);
    }

    @Test
    void removeBeforeCreateShouldLeaveATombstoneThatRejectsTheOlderCreate() {
        upsertLikeTaskTemplate("TEST_LIKE_LIFECYCLE", "LIFETIME", 5, true);
        String relationKey = "like:" + uuid(9) + ":3:" + uuid(100);
        UUID relationInstanceId = uuid(710);

        triggerLikeRemoved("like-removed-2", 2L, relationKey, relationInstanceId);
        triggerLikeCreated(
                "like-created-1",
                1L,
                relationKey,
                relationInstanceId,
                uuid(9),
                Instant.parse("2026-03-22T10:30:00Z")
        );

        assertThat(progressValueOrZero("TEST_LIKE_LIFECYCLE")).isZero();
        assertThat(eventLogCount("TEST_LIKE_LIFECYCLE", contributionId(relationInstanceId))).isZero();
        assertThat(lifecycleVersion(relationKey)).isEqualTo(2L);
        assertThat(lifecycleActive(relationKey)).isFalse();
    }

    @Test
    void lateRemoveFromFirstLifecycleShouldNotDeleteSecondLifecycleContribution() {
        upsertLikeTaskTemplate("TEST_LIKE_LIFECYCLE", "LIFETIME", 5, true);
        String relationKey = "like:" + uuid(9) + ":3:" + uuid(100);
        UUID firstInstance = uuid(711);
        UUID secondInstance = uuid(712);
        Instant occurredAt = Instant.parse("2026-03-22T10:30:00Z");

        triggerLikeCreated("like-created-a", 1L, relationKey, firstInstance, uuid(9), occurredAt);
        triggerLikeCreated("like-created-b", 3L, relationKey, secondInstance, uuid(9), occurredAt.plusSeconds(2));
        triggerLikeRemoved("like-removed-a", 2L, relationKey, firstInstance);

        assertThat(progressValue("TEST_LIKE_LIFECYCLE")).isEqualTo(1);
        assertThat(eventLogCount("TEST_LIKE_LIFECYCLE", contributionId(firstInstance))).isZero();
        assertThat(eventLogCount("TEST_LIKE_LIFECYCLE", contributionId(secondInstance))).isEqualTo(1);
        assertThat(lifecycleVersion(relationKey)).isEqualTo(3L);
        assertThat(lifecycleInstance(relationKey)).isEqualTo(secondInstance);
        assertThat(lifecycleActive(relationKey)).isTrue();
    }

    @Test
    void duplicateVersionedLikeDeliveriesShouldApplyEachContributionTransitionOnce() {
        upsertLikeTaskTemplate("TEST_LIKE_LIFECYCLE", "LIFETIME", 5, true);
        String relationKey = "like:" + uuid(9) + ":3:" + uuid(100);
        UUID relationInstanceId = uuid(717);
        Instant occurredAt = Instant.parse("2026-03-22T10:30:00Z");

        triggerLikeCreated("like-created", 1L, relationKey, relationInstanceId, uuid(9), occurredAt);
        triggerLikeCreated("like-created", 1L, relationKey, relationInstanceId, uuid(9), occurredAt);

        assertThat(progressValue("TEST_LIKE_LIFECYCLE")).isEqualTo(1);
        assertThat(eventLogCount("TEST_LIKE_LIFECYCLE", contributionId(relationInstanceId))).isEqualTo(1);

        triggerLikeRemoved("like-removed", 2L, relationKey, relationInstanceId);
        triggerLikeRemoved("like-removed", 2L, relationKey, relationInstanceId);

        assertThat(progressValue("TEST_LIKE_LIFECYCLE")).isZero();
        assertThat(eventLogCount("TEST_LIKE_LIFECYCLE", contributionId(relationInstanceId))).isZero();
        assertThat(lifecycleVersion(relationKey)).isEqualTo(2L);
        assertThat(lifecycleActive(relationKey)).isFalse();
    }

    @Test
    void legacyRemoveWithoutInstanceShouldNotRevokeKnownVersionedLifecycle() {
        upsertLikeTaskTemplate("TEST_LIKE_LIFECYCLE", "LIFETIME", 5, true);
        String relationKey = "like:" + uuid(9) + ":3:" + uuid(100);
        UUID relationInstanceId = uuid(718);
        long durableVersion = 4_611_686_018_427_387_905L;

        triggerLikeCreated(
                "versioned-like-created",
                durableVersion,
                relationKey,
                relationInstanceId,
                uuid(9),
                Instant.parse("2026-03-22T10:30:00Z")
        );
        triggerLikeRemoved("legacy-like-removed", 1_800_000_000_000L, relationKey, null);

        assertThat(progressValue("TEST_LIKE_LIFECYCLE")).isEqualTo(1);
        assertThat(eventLogCount("TEST_LIKE_LIFECYCLE", contributionId(relationInstanceId))).isEqualTo(1);
        assertThat(lifecycleVersion(relationKey)).isEqualTo(durableVersion);
        assertThat(lifecycleInstance(relationKey)).isEqualTo(relationInstanceId);
        assertThat(lifecycleActive(relationKey)).isTrue();
    }

    @Test
    void everyDeliveryPermutationShouldConvergeToTheHighestLifecycleVersion() {
        upsertLikeTaskTemplate("TEST_LIKE_LIFECYCLE", "LIFETIME", 5, true);
        String relationKey = "like:" + uuid(9) + ":3:" + uuid(100);
        UUID firstInstance = uuid(713);
        UUID secondInstance = uuid(714);

        for (List<Integer> order : permutations(List.of(0, 1, 2, 3))) {
            resetLikeTaskState();
            for (int event : order) {
                deliverLifecycleEvent(event, relationKey, firstInstance, secondInstance);
            }

            assertThat(progressValueOrZero("TEST_LIKE_LIFECYCLE"))
                    .as("delivery order %s", order)
                    .isZero();
            assertThat(eventLogCount("TEST_LIKE_LIFECYCLE", contributionId(firstInstance)))
                    .as("first lifecycle contribution for order %s", order)
                    .isZero();
            assertThat(eventLogCount("TEST_LIKE_LIFECYCLE", contributionId(secondInstance)))
                    .as("second lifecycle contribution for order %s", order)
                    .isZero();
            assertThat(lifecycleVersion(relationKey)).as("delivery order %s", order).isEqualTo(4L);
            assertThat(lifecycleActive(relationKey)).as("delivery order %s", order).isFalse();
        }
    }

    @Test
    void firstVersionedLikeShouldMigrateLegacyRelationKeyContribution() {
        String relationKey = "like:" + uuid(9) + ":3:" + uuid(100);
        UUID relationInstanceId = uuid(715);

        service.processEvent(USER_ID, "LikeCreated", relationKey, LocalDate.of(2026, 3, 22));
        triggerLikeCreated(
                "modern-like-created",
                4_611_686_018_427_387_905L,
                relationKey,
                relationInstanceId,
                uuid(9),
                Instant.parse("2026-03-22T10:30:00Z")
        );

        assertThat(progressValue("LIFETIME_RECEIVE_LIKE")).isEqualTo(1);
        assertThat(eventLogCount("LIFETIME_RECEIVE_LIKE", relationKey)).isZero();
        assertThat(eventLogCount("LIFETIME_RECEIVE_LIKE", contributionId(relationInstanceId))).isEqualTo(1);

        triggerLikeRemoved(
                "modern-like-removed",
                4_611_686_018_427_387_906L,
                relationKey,
                relationInstanceId
        );

        assertThat(progressValue("LIFETIME_RECEIVE_LIKE")).isZero();
        assertThat(eventLogCount("LIFETIME_RECEIVE_LIKE", relationKey)).isZero();
    }

    @Test
    void firstModernLifecycleShouldReplaceLegacyContributionAcrossPeriods() {
        upsertLikeTaskTemplate("TEST_DAILY_LIKE_LIFECYCLE", "DAILY", 5, true);
        String relationKey = "like:" + uuid(9) + ":3:" + uuid(100);
        UUID relationInstanceId = uuid(716);

        service.processEvent(USER_ID, "LikeCreated", relationKey, LocalDate.of(2026, 3, 21));
        triggerLikeCreated(
                "modern-like-created",
                4_611_686_018_427_387_905L,
                relationKey,
                relationInstanceId,
                uuid(9),
                Instant.parse("2026-03-22T10:30:00Z")
        );

        assertThat(progressValueForPeriod("TEST_DAILY_LIKE_LIFECYCLE", "2026-03-21")).isZero();
        assertThat(progressValueForPeriod("TEST_DAILY_LIKE_LIFECYCLE", "2026-03-22")).isEqualTo(1);
        assertThat(eventLogCount("TEST_DAILY_LIKE_LIFECYCLE", relationKey)).isZero();
        assertThat(eventLogCount("TEST_DAILY_LIKE_LIFECYCLE", contributionId(relationInstanceId))).isEqualTo(1);
    }

    @Test
    void removingOverflowContributionShouldKeepProgressAtTarget() {
        upsertLikeTaskTemplate("TEST_LIKE_OVERFLOW", "LIFETIME", 3, true);
        for (int index = 0; index < 4; index++) {
            triggerLikeCreated(
                    "like-created-" + index,
                    1L,
                    "like:" + uuid(20 + index) + ":3:" + uuid(100),
                    uuid(720 + index),
                    uuid(20 + index),
                    Instant.parse("2026-03-22T10:30:00Z").plusSeconds(index)
            );
        }

        assertThat(progressValue("TEST_LIKE_OVERFLOW")).isEqualTo(3);
        assertThat(progressStatus("TEST_LIKE_OVERFLOW")).isEqualTo("CLAIMABLE");
        assertThat(taskEventLogCount("TEST_LIKE_OVERFLOW")).isEqualTo(4);

        triggerLikeRemoved(
                "like-removed-0",
                2L,
                "like:" + uuid(20) + ":3:" + uuid(100),
                uuid(720)
        );

        assertThat(taskEventLogCount("TEST_LIKE_OVERFLOW")).isEqualTo(3);
        assertThat(progressValue("TEST_LIKE_OVERFLOW")).isEqualTo(3);
        assertThat(progressStatus("TEST_LIKE_OVERFLOW")).isEqualTo("CLAIMABLE");
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

    private int progressValueOrZero(String taskCode) {
        Integer value = jdbcTemplate.queryForObject(
                "select coalesce(max(current_value), 0) from user_task_progress where user_id = ? and task_code = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(USER_ID),
                taskCode
        );
        return value == null ? 0 : value;
    }

    private int progressValueForPeriod(String taskCode, String periodKey) {
        Integer value = jdbcTemplate.queryForObject(
                "select current_value from user_task_progress where user_id = ? and task_code = ? and period_key = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(USER_ID),
                taskCode,
                periodKey
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

    private int taskEventLogCount(String taskCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_task_event_log where user_id = ? and task_code = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(USER_ID),
                taskCode
        );
        return count == null ? 0 : count;
    }

    private void triggerLikeCreated(
            String eventId,
            long version,
            String relationKey,
            UUID relationInstanceId,
            UUID actorUserId,
            Instant occurredAt
    ) {
        service.triggerLikeCreated(new TriggerLikeCreatedCommand(
                eventId,
                version,
                relationKey,
                relationInstanceId,
                actorUserId,
                USER_ID,
                occurredAt
        ));
    }

    private void triggerLikeRemoved(
            String eventId,
            long version,
            String relationKey,
            UUID relationInstanceId
    ) {
        service.triggerLikeRemoved(new TriggerLikeRemovedCommand(
                eventId,
                version,
                relationKey,
                relationInstanceId,
                USER_ID
        ));
    }

    private void deliverLifecycleEvent(
            int event,
            String relationKey,
            UUID firstInstance,
            UUID secondInstance
    ) {
        Instant occurredAt = Instant.parse("2026-03-22T10:30:00Z");
        switch (event) {
            case 0 -> triggerLikeCreated("like-created-a", 1L, relationKey, firstInstance, uuid(9), occurredAt);
            case 1 -> triggerLikeRemoved("like-removed-a", 2L, relationKey, firstInstance);
            case 2 -> triggerLikeCreated("like-created-b", 3L, relationKey, secondInstance, uuid(9), occurredAt.plusSeconds(2));
            case 3 -> triggerLikeRemoved("like-removed-b", 4L, relationKey, secondInstance);
            default -> throw new IllegalArgumentException("unknown lifecycle event: " + event);
        }
    }

    private void resetLikeTaskState() {
        jdbcTemplate.update("delete from growth_like_task_lifecycle_state");
        jdbcTemplate.update("delete from user_task_event_log");
        jdbcTemplate.update("delete from user_task_progress");
    }

    private List<List<Integer>> permutations(List<Integer> values) {
        List<List<Integer>> result = new ArrayList<>();
        collectPermutations(new ArrayList<>(), new ArrayList<>(values), result);
        return result;
    }

    private void collectPermutations(
            List<Integer> prefix,
            List<Integer> remaining,
            List<List<Integer>> result
    ) {
        if (remaining.isEmpty()) {
            result.add(List.copyOf(prefix));
            return;
        }
        for (int index = 0; index < remaining.size(); index++) {
            Integer value = remaining.remove(index);
            prefix.add(value);
            collectPermutations(prefix, remaining, result);
            prefix.remove(prefix.size() - 1);
            remaining.add(index, value);
        }
    }

    private boolean lifecycleActive(String relationKey) {
        Boolean active = jdbcTemplate.queryForObject(
                "select active from growth_like_task_lifecycle_state where recipient_user_id = ? and relation_key = ?",
                Boolean.class,
                BinaryUuidCodec.toBytes(USER_ID),
                relationKey
        );
        return Boolean.TRUE.equals(active);
    }

    private long lifecycleVersion(String relationKey) {
        Long version = jdbcTemplate.queryForObject(
                "select source_version from growth_like_task_lifecycle_state where recipient_user_id = ? and relation_key = ?",
                Long.class,
                BinaryUuidCodec.toBytes(USER_ID),
                relationKey
        );
        return version == null ? 0L : version;
    }

    private UUID lifecycleInstance(String relationKey) {
        byte[] value = jdbcTemplate.queryForObject(
                "select relation_instance_id from growth_like_task_lifecycle_state where recipient_user_id = ? and relation_key = ?",
                byte[].class,
                BinaryUuidCodec.toBytes(USER_ID),
                relationKey
        );
        return BinaryUuidCodec.fromBytes(value);
    }

    private String contributionId(UUID relationInstanceId) {
        return "like-instance:" + relationInstanceId;
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
