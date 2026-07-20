package com.nowcoder.community.content.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.application.command.TakeModerationActionCommand;
import com.nowcoder.community.content.domain.model.ReportStatuses;
import com.nowcoder.community.content.domain.repository.ReportRepository;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class ModerationConcurrencySpringTest {

    private static final UUID REPORT_ID = uuid(500);
    private static final UUID REPORTER_ID = uuid(501);
    private static final UUID TARGET_USER_ID = uuid(502);
    private static final UUID FIRST_ACTOR_ID = uuid(503);
    private static final UUID SECOND_ACTOR_ID = uuid(504);
    private static final long INITIAL_POLICY_COUNTER = 70L;
    private static final long INITIAL_SECURITY_COUNTER = 60L;
    private static final long INITIAL_USER_POLICY_VERSION = 7L;
    private static final long INITIAL_USER_SECURITY_VERSION = 6L;
    private static final String CONTENT_TOPIC = "eventbus.content";
    private static final String USER_TOPIC = "eventbus.user";

    @Autowired
    private ModerationApplicationService applicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private ReportRepository reportRepository;

    @SpyBean
    private UserModerationActionApi userModerationActionApi;

    @SpyBean
    private JdbcOutboxEventStore outboxEventStore;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        reset(reportRepository, userModerationActionApi, outboxEventStore);
        jdbcTemplate.update("delete from moderation_action");
        jdbcTemplate.update("delete from report");
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from user where id = ?", bytes(TARGET_USER_ID));
        jdbcTemplate.update(
                "update user_policy_version_counter set current_version = ? where id = 1",
                INITIAL_POLICY_COUNTER
        );
        jdbcTemplate.update(
                "update user_security_version_counter set current_version = ? where id = 1",
                INITIAL_SECURITY_COUNTER
        );
        insertTargetUser();
        insertPendingReport();
    }

    @Test
    void concurrentSameDecisionShouldReturnOneActionIdAndCommitOneEffectSet() throws Exception {
        List<Attempt> attempts = runWithOverlappingClaims(
                command(FIRST_ACTOR_ID, " BAN ", " abuse ", 3600),
                command(SECOND_ACTOR_ID, "ban", "abuse", 3600)
        );

        assertThat(attempts).allSatisfy(attempt -> assertThat(attempt.failure()).isNull());
        assertThat(attempts).extracting(Attempt::actionId).doesNotContainNull().containsOnly(attempts.get(0).actionId());
        assertCommittedEffectSet("ban");
        verify(userModerationActionApi, times(1)).applyModeration(TARGET_USER_ID, "ban", 3600);
    }

    @Test
    void concurrentDifferentDecisionsShouldCommitOneAndConflictTheOther() throws Exception {
        List<Attempt> attempts = runWithOverlappingClaims(
                command(FIRST_ACTOR_ID, "ban", "abuse", 3600),
                command(SECOND_ACTOR_ID, "mute", "abuse", 3600)
        );

        assertThat(attempts.stream().filter(Attempt::succeeded)).hasSize(1);
        assertThat(attempts.stream().map(Attempt::failure).filter(BusinessException.class::isInstance))
                .singleElement()
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ContentErrorCode.MODERATION_DECISION_CONFLICT));
        assertCommittedEffectSet(action());
        verify(userModerationActionApi, times(1)).applyModeration(eq(TARGET_USER_ID), anyString(), eq(3600));
    }

    @Test
    void ownerSideEffectFailureShouldRollbackClaimAndOwnerRows() {
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            assertThat(reportStatus()).isEqualTo(ReportStatuses.PROCESSING);
            assertThat(userPolicyVersion()).isEqualTo(INITIAL_POLICY_COUNTER + 1L);
            assertThat(outboxCount(USER_TOPIC, TARGET_USER_ID)).isOne();
            throw new IllegalStateException("owner side effect failed");
        }).when(userModerationActionApi).applyModeration(TARGET_USER_ID, "ban", 3600);

        assertThatThrownBy(() -> applicationService.takeAction(
                command(FIRST_ACTOR_ID, "ban", "abuse", 3600)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("owner side effect failed");

        assertRolledBack();
    }

    @Test
    void noticeOutboxFailureShouldRollbackClaimActionAndOwnerRows() {
        doAnswer(invocation -> {
            Object inserted = invocation.callRealMethod();
            assertThat(inserted).isEqualTo(true);
            assertThat(reportStatus()).isEqualTo(ReportStatuses.PROCESSED);
            assertThat(actionCount()).isOne();
            assertThat(userPolicyVersion()).isEqualTo(INITIAL_POLICY_COUNTER + 1L);
            assertThat(outboxCount(USER_TOPIC, TARGET_USER_ID)).isOne();
            assertThat(outboxCount(CONTENT_TOPIC, TARGET_USER_ID)).isOne();
            throw new IllegalStateException("notice outbox insert failed");
        }).when(outboxEventStore).enqueue(anyString(), eq(CONTENT_TOPIC), eq(TARGET_USER_ID.toString()), anyString());

        assertThatThrownBy(() -> applicationService.takeAction(
                command(FIRST_ACTOR_ID, "ban", "abuse", 3600)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notice outbox insert failed");

        assertRolledBack();
    }

    @Test
    void terminalTransitionMissShouldRollbackClaimActionAndOwnerRows() {
        doAnswer(invocation -> {
            assertThat(reportStatus()).isEqualTo(ReportStatuses.PROCESSING);
            assertThat(actionCount()).isOne();
            assertThat(userPolicyVersion()).isEqualTo(INITIAL_POLICY_COUNTER + 1L);
            assertThat(outboxCount(USER_TOPIC, TARGET_USER_ID)).isOne();
            return false;
        }).when(reportRepository).transitionStatus(
                REPORT_ID,
                ReportStatuses.PROCESSING,
                ReportStatuses.PROCESSED
        );

        assertThatThrownBy(() -> applicationService.takeAction(
                command(FIRST_ACTOR_ID, "ban", "abuse", 3600)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ContentErrorCode.INTERNAL_ERROR));

        assertRolledBack();
    }

    @Test
    void h2FixtureShouldEnforceUniqueNonNullReportAndAllowMultipleNullReports() {
        insertAction(uuid(510), REPORT_ID);

        assertThatThrownBy(() -> insertAction(uuid(511), REPORT_ID))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertAction(uuid(512), null);
        insertAction(uuid(513), null);
        assertThat(actionCount()).isEqualTo(3);
        assertThat(requiredLong("select count(*) from moderation_action where report_id is null")).isEqualTo(2L);
    }

    private List<Attempt> runWithOverlappingClaims(
            TakeModerationActionCommand firstCommand,
            TakeModerationActionCommand secondCommand
    ) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch secondClaimEntered = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger claimOrder = new AtomicInteger();
        doAnswer(invocation -> {
            int order = claimOrder.incrementAndGet();
            if (order == 1) {
                Object result = invocation.callRealMethod();
                firstClaimed.countDown();
                if (!releaseWinner.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("winner claim was not released");
                }
                return result;
            }
            secondClaimEntered.countDown();
            return invocation.callRealMethod();
        }).when(reportRepository).claimPending(REPORT_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt> first = executor.submit(() -> attempt(start, firstCommand));
            Future<Attempt> second = executor.submit(() -> attempt(start, secondCommand));
            start.countDown();
            try {
                assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(secondClaimEntered.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                releaseWinner.countDown();
            }
            return List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private Attempt attempt(CountDownLatch start, TakeModerationActionCommand command) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return Attempt.failed(new IllegalStateException("concurrent start was not released"));
            }
            return Attempt.succeeded(applicationService.takeAction(command));
        } catch (Throwable error) {
            return Attempt.failed(error);
        }
    }

    private void assertCommittedEffectSet(String expectedAction) {
        assertThat(reportStatus()).isEqualTo(ReportStatuses.PROCESSED);
        assertThat(actionCount()).isOne();
        assertThat(action()).isEqualTo(expectedAction);
        assertThat(userPolicyVersion()).isEqualTo(INITIAL_POLICY_COUNTER + 1L);
        assertThat(policyCounter()).isEqualTo(INITIAL_POLICY_COUNTER + 1L);
        assertThat(outboxCount(USER_TOPIC, TARGET_USER_ID)).isOne();
        assertThat(outboxCount(CONTENT_TOPIC, TARGET_USER_ID)).isOne();
        assertThat(outboxCount(CONTENT_TOPIC, REPORTER_ID)).isOne();
        if ("ban".equals(expectedAction)) {
            assertThat(userBanUntil()).isNotNull();
            assertThat(userMuteUntil()).isNull();
            assertThat(userSecurityVersion()).isEqualTo(INITIAL_SECURITY_COUNTER + 1L);
            assertThat(securityCounter()).isEqualTo(INITIAL_SECURITY_COUNTER + 1L);
        } else {
            assertThat(expectedAction).isEqualTo("mute");
            assertThat(userMuteUntil()).isNotNull();
            assertThat(userBanUntil()).isNull();
            assertThat(userSecurityVersion()).isEqualTo(INITIAL_USER_SECURITY_VERSION);
            assertThat(securityCounter()).isEqualTo(INITIAL_SECURITY_COUNTER);
        }
    }

    private void assertRolledBack() {
        assertThat(reportStatus()).isEqualTo(ReportStatuses.PENDING);
        assertThat(actionCount()).isZero();
        assertThat(userPolicyVersion()).isEqualTo(INITIAL_USER_POLICY_VERSION);
        assertThat(userSecurityVersion()).isEqualTo(INITIAL_USER_SECURITY_VERSION);
        assertThat(policyCounter()).isEqualTo(INITIAL_POLICY_COUNTER);
        assertThat(securityCounter()).isEqualTo(INITIAL_SECURITY_COUNTER);
        assertThat(userMuteUntil()).isNull();
        assertThat(userBanUntil()).isNull();
        assertThat(requiredLong("select count(*) from outbox_event")).isZero();
    }

    private TakeModerationActionCommand command(
            UUID actorId,
            String action,
            String reason,
            Integer durationSeconds
    ) {
        return new TakeModerationActionCommand(actorId, REPORT_ID, action, reason, durationSeconds);
    }

    private void insertTargetUser() {
        jdbcTemplate.update(
                """
                insert into user(
                    id, username, password, salt, email, type, status, header_url,
                    create_time, mute_until, ban_until, policy_version, security_version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                bytes(TARGET_USER_ID),
                "moderation-concurrency-target",
                "encoded-password",
                "salt",
                "moderation-concurrency-target@example.com",
                0,
                0,
                "header",
                Timestamp.from(Instant.parse("2026-07-18T00:00:00Z")),
                null,
                null,
                INITIAL_USER_POLICY_VERSION,
                INITIAL_USER_SECURITY_VERSION
        );
    }

    private void insertPendingReport() {
        jdbcTemplate.update(
                """
                insert into report(
                    id, reporter_id, target_type, target_id, reason, detail, status, create_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                bytes(REPORT_ID),
                bytes(REPORTER_ID),
                EntityTypes.USER,
                bytes(TARGET_USER_ID),
                "abuse",
                "concurrency fixture",
                ReportStatuses.PENDING,
                Timestamp.from(Instant.parse("2026-07-18T01:00:00Z"))
        );
    }

    private void insertAction(UUID actionId, UUID reportId) {
        jdbcTemplate.update(
                """
                insert into moderation_action(
                    id, report_id, actor_id, action, reason, duration_seconds, create_time
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                bytes(actionId),
                reportId == null ? null : bytes(reportId),
                bytes(FIRST_ACTOR_ID),
                "ban",
                "abuse",
                3600,
                Timestamp.from(Instant.parse("2026-07-18T02:00:00Z"))
        );
    }

    private int reportStatus() {
        return requiredInteger("select status from report where id = ?", bytes(REPORT_ID));
    }

    private int actionCount() {
        return Math.toIntExact(requiredLong(
                "select count(*) from moderation_action where report_id = ? or report_id is null",
                bytes(REPORT_ID)
        ));
    }

    private String action() {
        return jdbcTemplate.queryForObject(
                "select action from moderation_action where report_id = ?",
                String.class,
                bytes(REPORT_ID)
        );
    }

    private long userPolicyVersion() {
        return requiredLong("select policy_version from user where id = ?", bytes(TARGET_USER_ID));
    }

    private long userSecurityVersion() {
        return requiredLong("select security_version from user where id = ?", bytes(TARGET_USER_ID));
    }

    private Timestamp userMuteUntil() {
        return jdbcTemplate.queryForObject(
                "select mute_until from user where id = ?",
                Timestamp.class,
                bytes(TARGET_USER_ID)
        );
    }

    private Timestamp userBanUntil() {
        return jdbcTemplate.queryForObject(
                "select ban_until from user where id = ?",
                Timestamp.class,
                bytes(TARGET_USER_ID)
        );
    }

    private long policyCounter() {
        return requiredLong("select current_version from user_policy_version_counter where id = 1");
    }

    private long securityCounter() {
        return requiredLong("select current_version from user_security_version_counter where id = 1");
    }

    private int outboxCount(String topic, UUID eventKey) {
        return Math.toIntExact(requiredLong(
                "select count(*) from outbox_event where topic = ? and event_key = ?",
                topic,
                eventKey.toString()
        ));
    }

    private int requiredInteger(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? -1 : value;
    }

    private long requiredLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? -1L : value;
    }

    private byte[] bytes(UUID value) {
        return BinaryUuidCodec.toBytes(value);
    }

    private record Attempt(UUID actionId, Throwable failure) {

        static Attempt succeeded(UUID actionId) {
            return new Attempt(actionId, null);
        }

        static Attempt failed(Throwable failure) {
            return new Attempt(null, failure);
        }

        boolean succeeded() {
            return failure == null;
        }
    }
}
