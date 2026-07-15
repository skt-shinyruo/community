package com.nowcoder.community.user.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.user.application.command.ApplyUserModerationCommand;
import com.nowcoder.community.user.application.command.UpdateUserRoleCommand;
import com.nowcoder.community.user.infrastructure.audit.Slf4jUserAuditLogAdapter;
import com.nowcoder.community.user.infrastructure.event.OutboxUserPolicyEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private static final long INITIAL_POLICY_COUNTER = 50L;
    private static final long INITIAL_SECURITY_COUNTER = 40L;
    private static final long INITIAL_USER_POLICY_VERSION = 5L;
    private static final long INITIAL_USER_SECURITY_VERSION = 7L;

    @Autowired
    private AdminUserApplicationService adminUserApplicationService;

    @Autowired
    private UserModerationApplicationService userModerationApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private Slf4jUserAuditLogAdapter userAuditLogAdapter;

    @SpyBean
    private OutboxUserPolicyEventPublisher userPolicyEventPublisher;

    @BeforeEach
    void setUp() {
        reset(userAuditLogAdapter, userPolicyEventPublisher);
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from user where id = ?", BinaryUuidCodec.toBytes(TARGET_USER_ID));
        jdbcTemplate.update(
                "update user_policy_version_counter set current_version = ? where id = 1",
                INITIAL_POLICY_COUNTER
        );
        jdbcTemplate.update(
                "update user_security_version_counter set current_version = ? where id = 1",
                INITIAL_SECURITY_COUNTER
        );
        jdbcTemplate.update(
                """
                insert into user(
                    id, username, password, salt, email, type, status, header_url,
                    create_time, mute_until, ban_until, policy_version, security_version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                BinaryUuidCodec.toBytes(TARGET_USER_ID),
                "transaction-target",
                "encoded-password",
                "salt",
                "transaction-target@example.com",
                0,
                0,
                "header",
                Timestamp.from(Instant.parse("2026-07-15T00:00:00Z")),
                null,
                null,
                INITIAL_USER_POLICY_VERSION,
                INITIAL_USER_SECURITY_VERSION
        );
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

        assertThatThrownBy(() -> adminUserApplicationService.updateRole(new UpdateUserRoleCommand(
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
                new ApplyUserModerationCommand(TARGET_USER_ID, "ban", 120)
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

    private int userType() {
        return requiredValue("select type from user where id = ?", Integer.class);
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

    private <T> T requiredValue(String sql, Class<T> type) {
        return jdbcTemplate.queryForObject(sql, type, BinaryUuidCodec.toBytes(TARGET_USER_ID));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
