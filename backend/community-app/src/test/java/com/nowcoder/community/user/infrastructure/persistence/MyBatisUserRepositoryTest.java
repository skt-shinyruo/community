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
        jdbcTemplate.update("delete from user");
    }

    @Test
    void findMethodsShouldMapDataObjectToDomainAccount() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 2, 1, "h7", createTime, 120, null, null);

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
                UserAccount::score
        ).containsExactly(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 2, 1, "h7", createTime, 120);
    }

    @Test
    void summaryAndProfileMethodsShouldProjectDomainViews() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 2, 1, "h7", createTime, 120, null, null);
        insertUser(BOB_ID, "bob", "encoded", "salt", "bob@example.com", 1, 1, "h8", createTime, 50, null, null);

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
                UserProfile::createTime,
                UserProfile::score
        ).containsExactly(ALICE_ID, "alice", "h7", 2, 1, createTime, 120);
    }

    @Test
    void updateMethodsShouldPersistUserWriteFields() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        Instant muteUntil = Instant.parse("2026-04-28T10:15:30Z");
        Instant banUntil = Instant.parse("2026-04-29T10:15:30Z");
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "old", createTime, 120, null, null);

        userRepository.updateHeaderUrl(ALICE_ID, "new-header");
        userRepository.updateRole(ALICE_ID, 2);
        userRepository.updatePassword(ALICE_ID, "new-password");
        userRepository.updateModerationUntil(ALICE_ID, muteUntil, banUntil);

        UserAccount updated = userRepository.findById(ALICE_ID).orElseThrow();
        assertThat(updated.headerUrl()).isEqualTo("new-header");
        assertThat(updated.type()).isEqualTo(2);
        assertThat(updated.encodedPassword()).isEqualTo("new-password");
        assertThat(updated.muteUntil()).isEqualTo(muteUntil);
        assertThat(updated.banUntil()).isEqualTo(banUntil);
    }

    @Test
    void updateMethodsShouldRaiseInternalErrorWhenNoRowsChanged() {
        assertThatThrownBy(() -> userRepository.updateRole(MISSING_ID, 2))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(INTERNAL_ERROR))
                .hasMessage("更新用户角色失败");
    }

    @Test
    void scanModerationStatesAfterIdShouldMapTimestampsAndPreserveMapperOrder() {
        Date createTime = Date.from(Instant.parse("2026-04-27T10:15:30Z"));
        Instant aliceMute = Instant.parse("2026-04-28T10:15:30Z");
        Instant bobBan = Instant.parse("2026-04-29T10:15:30Z");
        insertUser(ALICE_ID, "alice", "encoded", "salt", "alice@example.com", 0, 1, "h7", createTime, 120, aliceMute, null);
        insertUser(BOB_ID, "bob", "encoded", "salt", "bob@example.com", 0, 1, "h8", createTime, 50, null, bobBan);

        List<UserModerationStatus> statuses = userRepository.scanModerationStatesAfterId(new UUID(0L, 0L), 20);

        assertThat(statuses).extracting(UserModerationStatus::userId).containsExactly(ALICE_ID, BOB_ID);
        assertThat(statuses.get(0).muteUntil()).isEqualTo(aliceMute);
        assertThat(statuses.get(0).banUntil()).isNull();
        assertThat(statuses.get(1).muteUntil()).isNull();
        assertThat(statuses.get(1).banUntil()).isEqualTo(bobBan);
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
            int score,
            Instant muteUntil,
            Instant banUntil
    ) {
        jdbcTemplate.update(
                """
                        insert into user (id, username, password, salt, email, type, status, header_url, create_time, score, mute_until, ban_until)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                score,
                muteUntil == null ? null : Date.from(muteUntil),
                banUntil == null ? null : Date.from(banUntil)
        );
    }
}
