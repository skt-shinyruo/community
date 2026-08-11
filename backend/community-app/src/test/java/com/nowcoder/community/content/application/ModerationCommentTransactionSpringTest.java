package com.nowcoder.community.content.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxWorkerScheduler;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.application.ModerationApplicationService.TakeModerationActionCommand;
import com.nowcoder.community.content.domain.model.ReportStatuses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class ModerationCommentTransactionSpringTest {

    private static final UUID REPORT_ID = uuid(9301);
    private static final UUID REPORTER_ID = uuid(9302);
    private static final UUID ACTOR_ID = uuid(9303);
    private static final UUID COMMENT_AUTHOR_ID = uuid(9304);
    private static final UUID POST_ID = uuid(9305);
    private static final UUID COMMENT_ID = uuid(9306);
    private static final String CONTENT_TOPIC = "eventbus.content";

    @Autowired
    private ModerationApplicationService applicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private JdbcOutboxEventStore outboxEventStore;

    @MockitoBean
    private OutboxWorkerScheduler outboxWorkerScheduler;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        reset(outboxEventStore);
        jdbcTemplate.update("delete from moderation_action where report_id = ?", bytes(REPORT_ID));
        jdbcTemplate.update("delete from report where id = ?", bytes(REPORT_ID));
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from comment where id = ?", bytes(COMMENT_ID));
        jdbcTemplate.update("delete from discuss_post where id = ?", bytes(POST_ID));
        jdbcTemplate.update("delete from user where id in (?, ?)",
                bytes(ACTOR_ID), bytes(COMMENT_AUTHOR_ID));
        insertUser(ACTOR_ID, "moderation-comment-actor", 1, 1);
        insertUser(COMMENT_AUTHOR_ID, "moderation-comment-author", 0, 0);
        insertPostAndComment();
        insertPendingReport();
    }

    @Test
    void noticeFailureShouldRollbackCommentTombstoneAndModerationAuditTogether() {
        doAnswer(invocation -> {
            Object inserted = invocation.callRealMethod();
            assertThat(inserted).isEqualTo(true);
            assertThat(commentStatus()).isEqualTo(1);
            assertThat(postCommentCount()).isZero();
            assertThat(reportStatus()).isEqualTo(ReportStatuses.PROCESSED);
            assertThat(actionCount()).isOne();
            assertThat(outboxCount()).isEqualTo(3);
            throw new IllegalStateException("moderation notice failed");
        }).when(outboxEventStore).enqueue(
                anyString(),
                eq(CONTENT_TOPIC),
                eq(REPORTER_ID.toString()),
                anyString()
        );

        assertThatThrownBy(() -> applicationService.takeAction(
                new TakeModerationActionCommand(
                        ACTOR_ID, REPORT_ID, "hide", "spam", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("moderation notice failed");

        assertThat(commentStatus()).isZero();
        assertThat(postCommentCount()).isOne();
        assertThat(reportStatus()).isEqualTo(ReportStatuses.PENDING);
        assertThat(actionCount()).isZero();
        assertThat(outboxCount()).isZero();
    }

    private void insertUser(UUID id, String username, int type, int status) {
        jdbcTemplate.update(
                """
                insert into user(
                    id, username, password, salt, email, type, status, header_url,
                    create_time, policy_version, security_version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                bytes(id),
                username,
                "encoded-password",
                "salt",
                username + "@example.com",
                type,
                status,
                "header",
                Timestamp.from(Instant.parse("2026-07-18T00:00:00Z")),
                0L,
                0L
        );
    }

    private void insertPostAndComment() {
        jdbcTemplate.update(
                """
                insert into discuss_post(
                    id, user_id, title, type, status, create_time,
                    comment_count, score, score_version, aggregate_version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                bytes(POST_ID),
                bytes(COMMENT_AUTHOR_ID),
                "moderation comment post",
                0,
                0,
                Timestamp.from(Instant.parse("2026-07-18T00:10:00Z")),
                1,
                0.0,
                1L,
                1L
        );
        jdbcTemplate.update(
                """
                insert into comment(
                    id, post_id, user_id, root_comment_id, parent_comment_id,
                    reply_to_user_id, content, status, create_time, version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                bytes(COMMENT_ID),
                bytes(POST_ID),
                bytes(COMMENT_AUTHOR_ID),
                bytes(COMMENT_ID),
                null,
                null,
                "reported comment",
                0,
                Timestamp.from(Instant.parse("2026-07-18T00:20:00Z")),
                0L
        );
    }

    private void insertPendingReport() {
        jdbcTemplate.update(
                """
                insert into report(
                    id, reporter_id, target_type, target_id,
                    reason, detail, status, create_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                bytes(REPORT_ID),
                bytes(REPORTER_ID),
                EntityTypes.COMMENT,
                bytes(COMMENT_ID),
                "spam",
                "transaction fixture",
                ReportStatuses.PENDING,
                Timestamp.from(Instant.parse("2026-07-18T01:00:00Z"))
        );
    }

    private int commentStatus() {
        return requiredInteger(
                "select status from comment where id = ?", bytes(COMMENT_ID));
    }

    private int postCommentCount() {
        return requiredInteger(
                "select comment_count from discuss_post where id = ?", bytes(POST_ID));
    }

    private int reportStatus() {
        return requiredInteger(
                "select status from report where id = ?", bytes(REPORT_ID));
    }

    private int actionCount() {
        return requiredInteger(
                "select count(*) from moderation_action where report_id = ?",
                bytes(REPORT_ID));
    }

    private int outboxCount() {
        return requiredInteger("select count(*) from outbox_event");
    }

    private int requiredInteger(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? -1 : value;
    }

    private static byte[] bytes(UUID value) {
        return BinaryUuidCodec.toBytes(value);
    }
}
