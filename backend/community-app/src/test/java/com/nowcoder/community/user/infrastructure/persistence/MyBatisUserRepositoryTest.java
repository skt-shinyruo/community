package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import com.nowcoder.community.user.domain.model.UserProfile;
import com.nowcoder.community.user.domain.model.UserSummary;
import com.nowcoder.community.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_MODERATION_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MyBatisUserRepositoryTest {

    private static final UUID ALICE_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");
    private static final UUID BOB_ID = UUID.fromString("00000000-0000-7000-8000-000000000008");
    private static final UUID MISSING_ID = UUID.fromString("00000000-0000-7000-8000-000000000099");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from auth_refresh_token");
        jdbcTemplate.update("delete from user_policy_version_log");
        jdbcTemplate.update("delete from user");
        jdbcTemplate.update("update user_policy_version_counter set current_version = 0 where id = 1");
        jdbcTemplate.update("update user_security_version_counter set current_version = 0 where id = 1");
    }

    @Test
    void findMethodsShouldMapDataObjectToDomainAccount() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 2, 1, "h7", createTime, null, null);

        Optional<UserAccount> byId = userRepository.findById(ALICE_ID);
        Optional<UserAccount> byName = userRepository.findByUsername("alice");
        Optional<UserAccount> byEmail = userRepository.findByEmail("alice@example.com");

        assertThat(byId).isPresent();
        assertThat(byName).contains(byId.orElseThrow());
        assertThat(byEmail).contains(byId.orElseThrow());
        assertThat(byId.orElseThrow()).extracting(
                UserAccount::id,
                UserAccount::username,
                UserAccount::encodedPassword,
                UserAccount::salt,
                UserAccount::email,
                UserAccount::type,
                UserAccount::status,
                UserAccount::headerUrl,
                UserAccount::createTime,
                UserAccount::securityVersion
        ).containsExactly(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 2, 1, "h7", createTime, 0L);
    }

    @Test
    void summaryAndProfileMethodsShouldProjectDomainViews() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 2, 1, "h7", createTime, null, null);
        insertUser(BOB_ID, "bob", "encoded", "salt", "bob@example.com", 1, 1, "h8", createTime, null, null);

        List<UserSummary> summaries = userRepository.listSummariesByIds(List.of(BOB_ID, ALICE_ID));
        UserProfile profile = userRepository.findProfileById(ALICE_ID).orElseThrow();

        assertThat(summaries).extracting(UserSummary::id).containsExactly(BOB_ID, ALICE_ID);
        assertThat(summaries).extracting(UserSummary::username).containsExactly("bob", "alice");
        assertThat(profile).extracting(
                UserProfile::id,
                UserProfile::username,
                UserProfile::headerUrl,
                UserProfile::type,
                UserProfile::status,
                UserProfile::createTime
        ).containsExactly(ALICE_ID, "alice", "h7", 2, 1, createTime);
    }

    @Test
    void updateMethodsShouldPersistUserWriteFields() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        Instant muteUntil = Instant.parse("2026-04-28T10:15:30Z");
        Instant banUntil = Instant.parse("2026-04-29T10:15:30Z");
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "old", createTime, null, null);

        userRepository.updateHeaderUrl(ALICE_ID, "new-header");
        long statusSecurityVersion = userRepository.nextUserSecurityVersion(ALICE_ID);
        userRepository.updateStatus(ALICE_ID, 0, statusSecurityVersion);
        assertThat(userRepository.findById(ALICE_ID).orElseThrow().securityVersion()).isEqualTo(statusSecurityVersion);
        long roleSecurityVersion = userRepository.nextUserSecurityVersion(ALICE_ID);
        userRepository.updateRole(ALICE_ID, 2, roleSecurityVersion);
        assertThat(userRepository.findById(ALICE_ID).orElseThrow().securityVersion()).isEqualTo(roleSecurityVersion);
        long passwordSecurityVersion = userRepository.nextUserSecurityVersion(ALICE_ID);
        userRepository.updatePassword(ALICE_ID, "new-password", passwordSecurityVersion);
        assertThat(userRepository.findById(ALICE_ID).orElseThrow().securityVersion()).isEqualTo(passwordSecurityVersion);
        long policyVersion = userRepository.nextUserPolicyVersion(ALICE_ID);
        userRepository.updateModerationUntil(ALICE_ID, muteUntil, banUntil, policyVersion, 0L, 0L);

        UserAccount updated = userRepository.findById(ALICE_ID).orElseThrow();
        assertThat(updated.headerUrl()).isEqualTo("new-header");
        assertThat(updated.type()).isEqualTo(2);
        assertThat(updated.encodedPassword()).isEqualTo("new-password");
        assertThat(updated.muteUntil()).isEqualTo(muteUntil);
        assertThat(updated.banUntil()).isEqualTo(banUntil);
        assertThat(updated.policyVersion()).isEqualTo(policyVersion);
        assertThat(passwordSecurityVersion).isGreaterThan(roleSecurityVersion);
        assertThat(updated.securityVersion()).isEqualTo(passwordSecurityVersion);
    }

    @Test
    void updateMethodsShouldRaiseInternalErrorWhenNoRowsChanged() {
        long before = userRepository.currentUserSecurityVersion();
        assertThatThrownBy(() -> userRepository.updateRole(MISSING_ID, 2, before + 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(INTERNAL_ERROR))
                .hasMessage("更新用户角色失败");
        assertThat(userRepository.currentUserSecurityVersion()).isEqualTo(before);
    }

    @Test
    void scanModerationStatesAtVersionAfterIdShouldMapTimestampsAndPreserveMapperOrder() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        Instant aliceMute = Instant.parse("2026-04-28T10:15:30Z");
        Instant bobBan = Instant.parse("2026-04-29T10:15:30Z");
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "h7", createTime, aliceMute, null);
        insertUser(BOB_ID, "bob", "encoded", "salt", "bob@example.com", 0, 1, "h8", createTime, null, bobBan);
        long aliceVersion = userRepository.nextUserPolicyVersion(ALICE_ID);
        userRepository.updateModerationUntil(ALICE_ID, aliceMute, null, aliceVersion, 0L, 0L);
        long bobVersion = userRepository.nextUserPolicyVersion(BOB_ID);
        userRepository.updateModerationUntil(BOB_ID, null, bobBan, bobVersion, 0L, 0L);

        List<UserModerationStatus> statuses = userRepository.scanModerationStatesAtVersionAfterId(
                userRepository.currentUserPolicyVersion(),
                new UUID(0L, 0L),
                20
        );

        assertThat(statuses).extracting(UserModerationStatus::userId).containsExactly(ALICE_ID, BOB_ID);
        assertThat(statuses.get(0).muteUntil()).isEqualTo(aliceMute);
        assertThat(statuses.get(0).banUntil()).isNull();
        assertThat(statuses.get(0).version()).isEqualTo(aliceVersion);
        assertThat(statuses.get(1).muteUntil()).isNull();
        assertThat(statuses.get(1).banUntil()).isEqualTo(bobBan);
        assertThat(statuses.get(1).version()).isEqualTo(bobVersion);
    }

    @Test
    void userPolicyVersionShouldMonotonicallyIncreaseAndPersistOnRows() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        Instant muteUntil = Instant.parse("2026-04-28T10:15:30Z");
        Instant banUntil = Instant.parse("2026-04-29T10:15:30Z");
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "h7", createTime, null, null);

        long first = userRepository.nextUserPolicyVersion(ALICE_ID);
        userRepository.updateModerationUntil(ALICE_ID, muteUntil, null, first, 0L, 0L);
        long second = userRepository.nextUserPolicyVersion(ALICE_ID);
        userRepository.updateModerationUntil(ALICE_ID, muteUntil, banUntil, second, 0L, first);

        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(userRepository.findById(ALICE_ID).orElseThrow().policyVersion()).isEqualTo(second);
        assertThat(userRepository.scanModerationStatesAtVersionAfterId(
                userRepository.currentUserPolicyVersion(),
                new UUID(0L, 0L),
                20
        ).get(0).version())
                .isEqualTo(second);
        UserModerationStatus atFirstVersion = userRepository.scanModerationStatesAtVersionAfterId(
                first,
                new UUID(0L, 0L),
                20
        ).get(0);
        assertThat(atFirstVersion.version()).isEqualTo(first);
        assertThat(atFirstVersion.muteUntil()).isEqualTo(muteUntil);
        assertThat(atFirstVersion.banUntil()).isNull();
        assertThat(userRepository.currentUserPolicyVersion()).isEqualTo(second);
    }

    @Test
    void userPolicyVersionShouldRecoverWhenCounterTrailsDurableHistory() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        Instant muteUntil = Instant.parse("2026-04-28T10:15:30Z");
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "h7", createTime, null, null);
        jdbcTemplate.update(
                """
                        insert into user_policy_version_log(
                            version, user_id, user_exists, mute_until, ban_until, occurred_at
                        ) values (?, ?, ?, ?, ?, current_timestamp)
                        """,
                42L,
                BinaryUuidCodec.toBytes(BOB_ID),
                true,
                null,
                null
        );
        jdbcTemplate.update("update user_policy_version_counter set current_version = 3 where id = 1");

        assertThat(userRepository.currentUserPolicyVersion()).isEqualTo(42L);
        long allocated = userRepository.nextUserPolicyVersion(ALICE_ID);
        userRepository.updateModerationUntil(ALICE_ID, muteUntil, null, allocated, 0L, 0L);

        assertThat(allocated).isEqualTo(43L);
        assertThat(userRepository.currentUserPolicyVersion()).isEqualTo(43L);
        assertThat(userRepository.findById(ALICE_ID).orElseThrow().policyVersion()).isEqualTo(43L);
    }

    @Test
    void userSecurityVersionShouldMonotonicallyIncreaseAndPersistOnRows() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "h7", createTime, null, null);

        long first = userRepository.nextUserSecurityVersion(ALICE_ID);
        userRepository.updateRole(ALICE_ID, 2, first);
        long second = userRepository.nextUserSecurityVersion(ALICE_ID);
        userRepository.updatePassword(ALICE_ID, "new-password", second);

        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(userRepository.findById(ALICE_ID).orElseThrow().securityVersion()).isEqualTo(second);
        assertThat(userRepository.currentUserSecurityVersion()).isEqualTo(second);
    }

    @Test
    void userSecurityVersionShouldRecoverWhenCounterTrailsPersistedTargetVersion() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "h7", createTime, null, null);
        jdbcTemplate.update(
                "update user set security_version = ? where id = ?",
                42L,
                BinaryUuidCodec.toBytes(ALICE_ID)
        );
        jdbcTemplate.update("update user_security_version_counter set current_version = 3 where id = 1");

        long allocated = userRepository.nextUserSecurityVersion(ALICE_ID);
        userRepository.updatePassword(ALICE_ID, "new-password", allocated);

        assertThat(allocated).isEqualTo(43L);
        assertThat(userRepository.currentUserSecurityVersion()).isEqualTo(43L);
        assertThat(userRepository.findById(ALICE_ID).orElseThrow().securityVersion()).isEqualTo(43L);
    }

    @Test
    void passwordSecurityVersionCasShouldRejectStaleExpectedVersionWithoutChangingUser() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "h7", createTime, null, null);

        long staleWriteVersion = userRepository.nextUserSecurityVersion(ALICE_ID);
        boolean staleUpdated = userRepository.updatePasswordIfSecurityVersion(
                ALICE_ID,
                "stale-password",
                staleWriteVersion,
                99L
        );

        assertThat(staleUpdated).isFalse();
        UserAccount unchanged = userRepository.findById(ALICE_ID).orElseThrow();
        assertThat(unchanged.encodedPassword()).isEqualTo("encoded");
        assertThat(unchanged.securityVersion()).isZero();

        long currentWriteVersion = userRepository.nextUserSecurityVersion(ALICE_ID);
        assertThat(userRepository.updatePasswordIfSecurityVersion(
                ALICE_ID,
                "current-password",
                currentWriteVersion,
                0L
        )).isTrue();
        UserAccount updated = userRepository.findById(ALICE_ID).orElseThrow();
        assertThat(updated.encodedPassword()).isEqualTo("current-password");
        assertThat(updated.securityVersion()).isEqualTo(currentWriteVersion);
    }

    @Test
    void updateModerationUntilShouldPersistSecurityVersionWhenProvided() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        Instant banUntil = Instant.parse("2026-04-29T10:15:30Z");
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "h7", createTime, null, null);

        long policyVersion = userRepository.nextUserPolicyVersion(ALICE_ID);
        long securityVersion = userRepository.nextUserSecurityVersion(ALICE_ID);

        userRepository.updateModerationUntil(ALICE_ID, null, banUntil, policyVersion, securityVersion, 0L);

        UserAccount updated = userRepository.findById(ALICE_ID).orElseThrow();
        assertThat(updated.banUntil()).isEqualTo(banUntil);
        assertThat(updated.policyVersion()).isEqualTo(policyVersion);
        assertThat(updated.securityVersion()).isEqualTo(securityVersion);
    }

    @Test
    void updateModerationUntilShouldRejectStalePolicyVersionWithoutOverwritingCurrentState() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        Instant muteUntil = Instant.parse("2026-04-28T10:15:30Z");
        Instant staleBanUntil = Instant.parse("2026-04-29T10:15:30Z");
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "h7", createTime, null, null);

        long first = userRepository.nextUserPolicyVersion(ALICE_ID);
        userRepository.updateModerationUntil(ALICE_ID, muteUntil, null, first, 0L, 0L);
        long staleWriteVersion = userRepository.nextUserPolicyVersion(ALICE_ID);

        assertThatThrownBy(() -> userRepository.updateModerationUntil(
                ALICE_ID,
                null,
                staleBanUntil,
                staleWriteVersion,
                0L,
                0L
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(USER_MODERATION_CONFLICT));

        UserAccount current = userRepository.findById(ALICE_ID).orElseThrow();
        assertThat(current.muteUntil()).isEqualTo(muteUntil);
        assertThat(current.banUntil()).isNull();
        assertThat(current.policyVersion()).isEqualTo(first);
    }

    private void insertUser(
            UUID id,
            String username,
            String password,
            String salt,
            String email,
            int type,
            int status,
            String headerUrl,
            Date createTime,
            Instant muteUntil,
            Instant banUntil
    ) {
        jdbcTemplate.update(
                """
                        insert into user (id, username, password, salt, email, type, status, header_url, create_time, mute_until, ban_until)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                BinaryUuidCodec.toBytes(id),
                username,
                password,
                salt,
                email,
                type,
                status,
                headerUrl,
                createTime,
                muteUntil == null ? null : Date.from(muteUntil),
                banUntil == null ? null : Date.from(banUntil)
        );
    }
}
