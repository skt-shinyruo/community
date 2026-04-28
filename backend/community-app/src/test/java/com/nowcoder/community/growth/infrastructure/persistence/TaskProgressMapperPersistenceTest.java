package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskEventLogMapper;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskProgressMapper;
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
class TaskProgressMapperPersistenceTest {

    private static final UUID TASK_PROGRESS_ID = UUID.fromString("00000000-0000-7000-8000-000000000611");
    private static final UUID TASK_EVENT_LOG_ID = UUID.fromString("00000000-0000-7000-8000-000000000621");
    private static final UUID USER_ID = uuid(1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserTaskProgressMapper userTaskProgressMapper;

    @Autowired
    private UserTaskEventLogMapper userTaskEventLogMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_task_event_log");
        jdbcTemplate.update("delete from user_task_progress");
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
}
