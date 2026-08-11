package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.growth.domain.model.TaskTemplate;
import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.TaskTemplateMapper;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskEventLogMapper;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskProgressMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class TaskProgressMapperPersistenceTest {

    private static final UUID TASK_PROGRESS_ID = UUID.fromString("00000000-0000-7000-8000-000000000611");
    private static final UUID TASK_EVENT_LOG_ID = UUID.fromString("00000000-0000-7000-8000-000000000621");
    private static final UUID USER_ID = uuid(1);
    private static final String LIKE_TASK_CODE = "LOCKING_LIKE_TASK";
    private static final String MAPPING_TASK_CODE = "MAPPING_TASK";
    private static final String LIKE_SOURCE_ID = "like-instance:" + TASK_EVENT_LOG_ID;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserTaskProgressMapper userTaskProgressMapper;

    @Autowired
    private TaskTemplateMapper taskTemplateMapper;

    @Autowired
    private UserTaskEventLogMapper userTaskEventLogMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_task_event_log");
        jdbcTemplate.update("delete from user_task_progress");
        jdbcTemplate.update("delete from task_template where task_code = ?", MAPPING_TASK_CODE);
    }

    @Test
    void userTaskProgressInsertShouldPersistApplicationAssignedUuidPrimaryKey() {
        int inserted = userTaskProgressMapper.insert(
                TASK_PROGRESS_ID,
                USER_ID,
                "DAILY_POST",
                "2026-03-22",
                1,
                "IN_PROGRESS",
                "post-evt-1"
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from user_task_progress where user_id = ? and task_code = ? and period_key = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(USER_ID),
                "DAILY_POST",
                "2026-03-22"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(TASK_PROGRESS_ID);

        UserTaskProgress progress = userTaskProgressMapper.selectByUserTaskAndPeriod(USER_ID, "DAILY_POST", "2026-03-22");
        assertThat(progress).isNotNull();
        assertThat(progress.getId()).isEqualTo(TASK_PROGRESS_ID);
        assertThat(progress.getUserId()).isEqualTo(USER_ID);
        assertThat(progress.getTaskCode()).isEqualTo("DAILY_POST");
        assertThat(progress.getPeriodKey()).isEqualTo("2026-03-22");
        assertThat(progress.getCurrentValue()).isZero();
        assertThat(progress.getTargetValue()).isOne();
        assertThat(progress.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(progress.getLastSourceEventId()).isEqualTo("post-evt-1");
        assertThat(progress.getUpdateTime()).isNotNull();
    }

    @Test
    void taskTemplateSelectShouldMapEveryDomainPropertyWithoutADataObjectSubclass() {
        jdbcTemplate.update(
                """
                        insert into task_template(
                            task_code, task_type, period_type, trigger_event_type, target_value,
                            reward_growth_delta, reward_balance_delta, claim_required, display_order,
                            status, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                MAPPING_TASK_CODE,
                "SOCIAL",
                "DAILY",
                "PostCreated",
                3,
                5,
                7,
                true,
                42,
                "ACTIVE"
        );

        TaskTemplate template = taskTemplateMapper.selectActiveByTriggerEventType("PostCreated").stream()
                .filter(candidate -> MAPPING_TASK_CODE.equals(candidate.getTaskCode()))
                .findFirst()
                .orElseThrow();

        assertThat(template.getTaskType()).isEqualTo("SOCIAL");
        assertThat(template.getPeriodType()).isEqualTo("DAILY");
        assertThat(template.getTriggerEventType()).isEqualTo("PostCreated");
        assertThat(template.getTargetValue()).isEqualTo(3);
        assertThat(template.getRewardGrowthDelta()).isEqualTo(5);
        assertThat(template.getRewardBalanceDelta()).isEqualTo(7);
        assertThat(template.isClaimRequired()).isTrue();
        assertThat(template.getDisplayOrder()).isEqualTo(42);
        assertThat(template.getStatus()).isEqualTo("ACTIVE");
        assertThat(template.getCreateTime()).isNotNull();
        assertThat(template.getUpdateTime()).isNotNull();
    }

    @Test
    void userTaskProgressUpdateShouldTargetUuidPrimaryKey() {
        jdbcTemplate.update(
                "insert into user_task_progress(id, user_id, task_code, period_key, current_value, target_value, status, update_time) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(TASK_PROGRESS_ID),
                BinaryUuidCodec.toBytes(USER_ID),
                "DAILY_POST",
                "2026-03-22",
                0,
                1,
                "IN_PROGRESS"
        );

        int updated = userTaskProgressMapper.updateProgress(
                TASK_PROGRESS_ID,
                1,
                "CLAIMED",
                null,
                null,
                "grant-1",
                "post-evt-1"
        );

        assertThat(updated).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select reward_grant_id from user_task_progress where id = ?",
                String.class,
                BinaryUuidCodec.toBytes(TASK_PROGRESS_ID)
        )).isEqualTo("grant-1");
    }

    @Test
    void userTaskEventLogInsertShouldPersistApplicationAssignedUuidPrimaryKey() {
        int inserted = userTaskEventLogMapper.insert(
                TASK_EVENT_LOG_ID,
                USER_ID,
                "DAILY_POST",
                "2026-03-22",
                "post-evt-1"
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from user_task_event_log where user_id = ? and task_code = ? and period_key = ? and source_event_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(USER_ID),
                "DAILY_POST",
                "2026-03-22",
                "post-evt-1"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(TASK_EVENT_LOG_ID);
    }

    @Test
    void likeContributionLookupShouldLockRowsBeforeProgressRecalculation() throws Exception {
        jdbcTemplate.update(
                "merge into task_template(task_code, task_type, period_type, trigger_event_type, "
                        + "target_value, reward_growth_delta, reward_balance_delta, claim_required, display_order, status) "
                        + "key(task_code) values (?, 'SOCIAL', 'DAILY', 'LikeCreated', 3, 0, 0, false, 99, 'ACTIVE')",
                LIKE_TASK_CODE
        );
        userTaskEventLogMapper.insert(
                TASK_EVENT_LOG_ID,
                USER_ID,
                LIKE_TASK_CODE,
                "2026-03-22",
                LIKE_SOURCE_ID
        );
        CountDownLatch lookupCompleted = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        CountDownLatch deletionStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> lookup = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(userTaskEventLogMapper.selectLikeContributionLogsForUpdate(USER_ID, LIKE_SOURCE_ID)).hasSize(1);
            lookupCompleted.countDown();
            await(releaseLookup);
        }));

        try {
            assertThat(lookupCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Integer> deletion = executor.submit(() -> {
                deletionStarted.countDown();
                return new TransactionTemplate(transactionManager).execute(status ->
                        userTaskEventLogMapper.deleteByUserTaskPeriodAndSourceEventId(
                                USER_ID,
                                LIKE_TASK_CODE,
                                "2026-03-22",
                                LIKE_SOURCE_ID
                        ));
            });
            assertThat(deletionStarted.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> deletion.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseLookup.countDown();
            lookup.get(5, TimeUnit.SECONDS);
            assertThat(deletion.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            releaseLookup.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for concurrent task progress operation");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for concurrent task progress operation", error);
        }
    }
}
