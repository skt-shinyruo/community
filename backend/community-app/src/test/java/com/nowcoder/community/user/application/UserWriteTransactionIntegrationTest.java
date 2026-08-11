package com.nowcoder.community.user.application;

import com.nowcoder.community.user.api.action.UserRegistrationActionApi;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.user.api.action.UserModerationActionApi.ApplyModerationCommand;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;
import com.nowcoder.community.user.infrastructure.audit.Slf4jUserAuditLogAdapter;
import com.nowcoder.community.user.infrastructure.event.OutboxUserPolicyEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class UserWriteTransactionIntegrationTest {

    private static final UUID ACTOR_USER_ID = uuid(91);
    private static final UUID TARGET_USER_ID = uuid(92);
    private static final UUID REGISTRATION_USER_ID = uuid(93);
    private static final long INITIAL_POLICY_COUNTER = 50L;
    private static final long INITIAL_SECURITY_COUNTER = 40L;
    private static final long INITIAL_USER_POLICY_VERSION = 5L;
    private static final long INITIAL_USER_SECURITY_VERSION = 7L;

    @Autowired
    private AdminUserApplicationService adminUserApplicationService;

    @Autowired
    private UserModerationApplicationService userModerationApplicationService;

    @Autowired
    private UserRegistrationApplicationService userRegistrationApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private Slf4jUserAuditLogAdapter userAuditLogAdapter;

    @MockitoSpyBean
    private OutboxUserPolicyEventPublisher userPolicyEventPublisher;

    @BeforeEach
    void setUp() {
        reset(userAuditLogAdapter, userPolicyEventPublisher);
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from user_policy_version_log");
        jdbcTemplate.update("delete from user where id = ?", BinaryUuidCodec.toBytes(ACTOR_USER_ID));
        jdbcTemplate.update("delete from user where id = ?", BinaryUuidCodec.toBytes(TARGET_USER_ID));
        jdbcTemplate.update("delete from user where id = ?", BinaryUuidCodec.toBytes(REGISTRATION_USER_ID));
        jdbcTemplate.update(
                "update user_policy_version_counter set current_version = ? where id = 1",
                INITIAL_POLICY_COUNTER
        );
        jdbcTemplate.update(
                "update user_security_version_counter set current_version = ? where id = 1",
                INITIAL_SECURITY_COUNTER
        );
        insertUser(
                ACTOR_USER_ID,
                "transaction-actor",
                "transaction-actor@example.com",
                1,
                1
        );
        insertUser(
                TARGET_USER_ID,
                "transaction-target",
                "transaction-target@example.com",
                0,
                1
        );
    }

    @Test
    void concurrentMutualAdminDowngradesMustLeaveOneActiveAdmin() throws Exception {
        jdbcTemplate.update(
                "update user set type = 1, status = 1 where id in (?, ?)",
                BinaryUuidCodec.toBytes(ACTOR_USER_ID),
                BinaryUuidCodec.toBytes(TARGET_USER_ID)
        );
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = executor.submit(() -> attemptRoleUpdate(
                    start,
                    new AdminUserApplicationService.UpdateRoleCommand(
                            ACTOR_USER_ID, TARGET_USER_ID, 2, "delegate target", true
                    )
            ));
            Future<Throwable> second = executor.submit(() -> attemptRoleUpdate(
                    start,
                    new AdminUserApplicationService.UpdateRoleCommand(
                            TARGET_USER_ID, ACTOR_USER_ID, 2, "delegate target", true
                    )
            ));

            start.countDown();
            List<Throwable> outcomes = Arrays.asList(first.get(), second.get());

            assertThat(outcomes).filteredOn(error -> error == null).hasSize(1);
            assertThat(outcomes).filteredOn(error -> error instanceof BusinessException).singleElement()
                    .satisfies(error -> {
                        assertThat(error).hasMessage("操作者不再具备有效管理员权限");
                        assertThat(((BusinessException) error).getErrorCode()).isEqualTo(FORBIDDEN);
                    });
            assertThat(activeAdminCount(ACTOR_USER_ID, TARGET_USER_ID)).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void roleAuditFailureMustRollbackRoleAndSecurityCounter() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(userType()).isEqualTo(2);
            assertThat(userSecurityVersion()).isEqualTo(INITIAL_SECURITY_COUNTER + 1L);
            assertThat(securityCounter()).isEqualTo(INITIAL_SECURITY_COUNTER + 1L);
            throw new IllegalStateException("audit failed");
        }).when(userAuditLogAdapter).recordRoleUpdated(any(), any(), any(Integer.class), any(Integer.class), any());

        assertThatThrownBy(() -> adminUserApplicationService.updateRole(new AdminUserApplicationService.UpdateRoleCommand(
                ACTOR_USER_ID,
                TARGET_USER_ID,
                2,
                "transaction rollback proof",
                true
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit failed");

        assertThat(userType()).isZero();
        assertThat(userSecurityVersion()).isEqualTo(INITIAL_USER_SECURITY_VERSION);
        assertThat(securityCounter()).isEqualTo(INITIAL_SECURITY_COUNTER);
    }

    @Test
    void moderationPublicationFailureMustRollbackPolicySecurityAndOutbox() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(userBanUntil()).isNotNull();
            assertThat(userPolicyVersion()).isEqualTo(INITIAL_POLICY_COUNTER + 1L);
            assertThat(userSecurityVersion()).isEqualTo(INITIAL_SECURITY_COUNTER + 1L);
            assertThat(policyCounter()).isEqualTo(INITIAL_POLICY_COUNTER + 1L);
            assertThat(securityCounter()).isEqualTo(INITIAL_SECURITY_COUNTER + 1L);
            assertThat(outboxCount()).isOne();
            throw new IllegalStateException("policy publication failed");
        }).when(userPolicyEventPublisher).publishUserPolicyChanged(any(), any());

        assertThatThrownBy(() -> userModerationApplicationService.applyModeration(
                new ApplyModerationCommand(ACTOR_USER_ID, TARGET_USER_ID, "ban", 120)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("policy publication failed");

        assertThat(userBanUntil()).isNull();
        assertThat(userPolicyVersion()).isEqualTo(INITIAL_USER_POLICY_VERSION);
        assertThat(userSecurityVersion()).isEqualTo(INITIAL_USER_SECURITY_VERSION);
        assertThat(policyCounter()).isEqualTo(INITIAL_POLICY_COUNTER);
        assertThat(securityCounter()).isEqualTo(INITIAL_SECURITY_COUNTER);
        assertThat(outboxCount()).isZero();
    }

    @Test
    void registrationMustReturnThePositiveSecurityVersionPersistedInItsTransaction() {
        UserRegistrationActionApi.VerifiedRegistrationResult result = userRegistrationApplicationService.createVerifiedRegistrationUser(
                registrationCommand()
        );

        long persistedSecurityVersion = requiredValue(
                REGISTRATION_USER_ID,
                "select security_version from user where id = ?",
                Long.class
        );
        assertThat(persistedSecurityVersion).isEqualTo(INITIAL_SECURITY_COUNTER + 1L);
        assertThat(persistedSecurityVersion).isPositive();
        assertThat(result.created()).isTrue();
        assertThat(result.user().securityVersion()).isEqualTo(persistedSecurityVersion);
        assertThat(securityCounter()).isEqualTo(persistedSecurityVersion);
        assertThat(requiredValue(
                REGISTRATION_USER_ID,
                "select policy_version from user where id = ?",
                Long.class
        )).isEqualTo(INITIAL_POLICY_COUNTER + 1L);
        assertThat(outboxCount()).isOne();
    }

    @Test
    void registrationPublicationFailureMustRollbackUserAndBothVersionCounters() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(registrationUserCount()).isOne();
            assertThat(requiredValue(
                    REGISTRATION_USER_ID,
                    "select security_version from user where id = ?",
                    Long.class
            )).isEqualTo(INITIAL_SECURITY_COUNTER + 1L);
            throw new IllegalStateException("registration publication failed");
        }).when(userPolicyEventPublisher).publishUserPolicyChanged(
                eq(REGISTRATION_USER_ID),
                eq(true),
                any(Instant.class),
                anyLong()
        );

        assertThatThrownBy(() -> userRegistrationApplicationService.createVerifiedRegistrationUser(
                registrationCommand()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("registration publication failed");

        assertThat(registrationUserCount()).isZero();
        assertThat(policyCounter()).isEqualTo(INITIAL_POLICY_COUNTER);
        assertThat(securityCounter()).isEqualTo(INITIAL_SECURITY_COUNTER);
        assertThat(outboxCount()).isZero();
    }

    private int userType() {
        return requiredValue("select type from user where id = ?", Integer.class);
    }

    private Throwable attemptRoleUpdate(
            CountDownLatch start,
            AdminUserApplicationService.UpdateRoleCommand command
    ) {
        try {
            start.await();
            adminUserApplicationService.updateRole(command);
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private long activeAdminCount(UUID firstUserId, UUID secondUserId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from user where id in (?, ?) and type = 1 and status != 0",
                Long.class,
                BinaryUuidCodec.toBytes(firstUserId),
                BinaryUuidCodec.toBytes(secondUserId)
        );
        return count == null ? 0L : count;
    }

    private void insertUser(UUID userId, String username, String email, int type, int status) {
        jdbcTemplate.update(
                """
                insert into user(
                    id, username, password, salt, email, type, status, header_url,
                    create_time, mute_until, ban_until, policy_version, security_version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                BinaryUuidCodec.toBytes(userId),
                username,
                "encoded-password",
                "salt",
                email,
                type,
                status,
                "header",
                Timestamp.from(Instant.parse("2026-07-15T00:00:00Z")),
                null,
                null,
                INITIAL_USER_POLICY_VERSION,
                INITIAL_USER_SECURITY_VERSION
        );
    }

    private long userPolicyVersion() {
        return requiredValue("select policy_version from user where id = ?", Long.class);
    }

    private long userSecurityVersion() {
        return requiredValue("select security_version from user where id = ?", Long.class);
    }

    private Timestamp userBanUntil() {
        return jdbcTemplate.queryForObject(
                "select ban_until from user where id = ?",
                Timestamp.class,
                BinaryUuidCodec.toBytes(TARGET_USER_ID)
        );
    }

    private long policyCounter() {
        return counter("user_policy_version_counter");
    }

    private long securityCounter() {
        return counter("user_security_version_counter");
    }

    private long counter(String table) {
        Long value = jdbcTemplate.queryForObject(
                "select current_version from " + table + " where id = 1",
                Long.class
        );
        return value == null ? 0L : value;
    }

    private long outboxCount() {
        Long value = jdbcTemplate.queryForObject("select count(*) from outbox_event", Long.class);
        return value == null ? 0L : value;
    }

    private long registrationUserCount() {
        Long value = jdbcTemplate.queryForObject(
                "select count(*) from user where id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(REGISTRATION_USER_ID)
        );
        return value == null ? 0L : value;
    }

    private VerifiedRegistrationUserCommand registrationCommand() {
        return new VerifiedRegistrationUserCommand(
                REGISTRATION_USER_ID,
                "transaction-registration",
                "transaction-registration@example.com",
                "$2a$10$7EqJtq98hPqEX7fNZaFWoOHiE9VYh4Vh7H1w52x1x7YjQwlhbR1XK",
                "registration-header"
        );
    }

    private <T> T requiredValue(String sql, Class<T> type) {
        return jdbcTemplate.queryForObject(sql, type, BinaryUuidCodec.toBytes(TARGET_USER_ID));
    }

    private <T> T requiredValue(UUID userId, String sql, Class<T> type) {
        return jdbcTemplate.queryForObject(sql, type, BinaryUuidCodec.toBytes(userId));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
