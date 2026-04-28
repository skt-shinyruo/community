package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.model.ModerationAction;
import com.nowcoder.community.content.domain.model.Report;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class ModerationActionMapperPersistenceTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000202");
    private static final UUID ACTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000203");
    private static final UUID REPORTER_ID = uuid(7);
    private static final UUID TARGET_ID = uuid(88);
    private static final UUID ACTOR_ID = uuid(99);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private ModerationActionMapper moderationActionMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from moderation_action");
        jdbcTemplate.update("delete from report");
    }

    @Test
    void insertActionShouldPersistUuidPrimaryKeyAndUuidReportReference() {
        Report report = new Report();
        report.setId(REPORT_ID);
        report.setReporterId(REPORTER_ID);
        report.setTargetType(1);
        report.setTargetId(TARGET_ID);
        report.setReason("spam");
        report.setDetail("details");
        report.setStatus(0);
        report.setCreateTime(new Date());
        reportMapper.insertReport(report);

        ModerationAction action = new ModerationAction();
        action.setId(ACTION_ID);
        action.setReportId(REPORT_ID);
        action.setActorId(ACTOR_ID);
        action.setAction("ban");
        action.setReason("abuse");
        action.setDurationSeconds(3600);
        action.setCreateTime(new Date());

        int inserted = moderationActionMapper.insertAction(action);

        assertThat(inserted).isEqualTo(1);
        assertThat(action.getId()).isEqualTo(ACTION_ID);
        assertThat(action.getReportId()).isEqualTo(REPORT_ID);

        byte[] storedId = jdbcTemplate.queryForObject(
                "select id from moderation_action where actor_id = ? and action = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(ACTOR_ID),
                "ban"
        );
        assertThat(storedId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedId)).isEqualTo(ACTION_ID);

        byte[] storedReportId = jdbcTemplate.queryForObject(
                "select report_id from moderation_action where id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                BinaryUuidCodec.toBytes(ACTION_ID)
        );
        assertThat(storedReportId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedReportId)).isEqualTo(REPORT_ID);

        List<ModerationAction> actions = moderationActionMapper.selectActionsByReportId(REPORT_ID);
        assertThat(actions).singleElement().satisfies(persisted -> {
            assertThat(persisted.getId()).isEqualTo(ACTION_ID);
            assertThat(persisted.getReportId()).isEqualTo(REPORT_ID);
            assertThat(persisted.getActorId()).isEqualTo(ACTOR_ID);
        });
    }
}
