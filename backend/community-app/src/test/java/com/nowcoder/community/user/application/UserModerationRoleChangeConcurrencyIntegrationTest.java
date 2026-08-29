package com.nowcoder.community.user.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.user.api.action.UserModerationActionApi.ApplyModerationCommand;
import com.nowcoder.community.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class UserModerationRoleChangeConcurrencyIntegrationTest {

    private static final UUID ADMIN_ID = uuid(191);
    private static final UUID MODERATOR_ID = uuid(192);
    private static final UUID TARGET_USER_ID = uuid(193);

    @Autowired
    private AdminUserApplicationService adminUserApplicationService;

    @Autowired
    private UserModerationApplicationService userModerationApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        reset(userRepository);
        jdbcTemplate.update("delete from outbox_event");
        deleteUser(ADMIN_ID);
        deleteUser(MODERATOR_ID);
        deleteUser(TARGET_USER_ID);
        jdbcTemplate.update("update user_policy_version_counter set current_version = 100 where id = 1");
        jdbcTemplate.update("update user_security_version_counter set current_version = 100 where id = 1");
        insertUser(ADMIN_ID, "role-race-admin", 1);
        insertUser(MODERATOR_ID, "role-race-moderator", 2);
        insertUser(TARGET_USER_ID, "role-race-target", 0);
    }

    @Test
    void moderationMustObserveRoleDowngradeCommittedUnderSharedLock() throws Exception {
        CountDownLatch roleWriteEntered = new CountDownLatch(1);
        CountDownLatch releaseRoleChange = new CountDownLatch(1);
        CountDownLatch moderationLockAttempted = new CountDownLatch(1);
        AtomicInteger lockCalls = new AtomicInteger();

        doAnswer(invocation -> {
            if (lockCalls.incrementAndGet() == 2) {
                moderationLockAttempted.countDown();
            }
            return invocation.callRealMethod();
        }).when(userRepository).lockRoleManagement();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            roleWriteEntered.countDown();
            if (!releaseRoleChange.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("role change was not released");
            }
            return null;
        }).when(userRepository).updateRole(any(), anyInt(), anyLong());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> roleChange = executor.submit(() -> attempt(() ->
                    adminUserApplicationService.updateRole(new AdminUserApplicationService.UpdateRoleCommand(
                            ADMIN_ID,
                            MODERATOR_ID,
                            0,
                            "revoke moderation role",
                            true
                    ))
            ));
            assertThat(roleWriteEntered.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> moderation = executor.submit(() -> attempt(() ->
                    userModerationApplicationService.applyModeration(new ApplyModerationCommand(
                            MODERATOR_ID,
                            TARGET_USER_ID,
                            "mute",
                            60
                    ))
            ));
            assertThat(moderationLockAttempted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(moderation).isNotDone();

            releaseRoleChange.countDown();

            assertThat(roleChange.get(10, TimeUnit.SECONDS)).isNull();
            Throwable rejection = moderation.get(10, TimeUnit.SECONDS);
            assertThat(rejection).isInstanceOf(BusinessException.class)
                    .hasMessage("普通用户无权执行用户处罚");
            assertThat(((BusinessException) rejection).getErrorCode()).isEqualTo(FORBIDDEN);
            assertThat(userType(MODERATOR_ID)).isZero();
            assertThat(userMuteUntil(TARGET_USER_ID)).isNull();
        } finally {
            releaseRoleChange.countDown();
            executor.shutdownNow();
        }
    }

    private Throwable attempt(ThrowingOperation operation) {
        try {
            operation.run();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private void deleteUser(UUID userId) {
        jdbcTemplate.update("delete from user where id = ?", BinaryUuidCodec.toBytes(userId));
    }

    private void insertUser(UUID userId, String username, int type) {
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
                username + "@example.com",
                type,
                1,
                "header",
                Timestamp.from(Instant.parse("2026-08-01T00:00:00Z")),
                null,
                null,
                0L,
                0L
        );
    }

    private int userType(UUID userId) {
        Integer type = jdbcTemplate.queryForObject(
                "select type from user where id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(userId)
        );
        return type == null ? -1 : type;
    }

    private Timestamp userMuteUntil(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select mute_until from user where id = ?",
                Timestamp.class,
                BinaryUuidCodec.toBytes(userId)
        );
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", suffix));
    }
}
