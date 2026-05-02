package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.user.domain.model.RefreshTokenSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MyBatisRefreshTokenSessionRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-00000000000a");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MyBatisRefreshTokenSessionRepository repository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from auth_refresh_token_family_revocation");
        jdbcTemplate.update("delete from auth_refresh_token");
    }

    @Test
    void storeShouldPersistUuidUserIdAsBinary16() {
        Instant expiresAt = Instant.parse("2026-04-21T03:00:00Z");

        repository.store("hash-1", USER_ID, "family-1", expiresAt);

        byte[] storedUserId = jdbcTemplate.queryForObject(
                "select user_id from auth_refresh_token where token_hash = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "hash-1"
        );
        assertThat(storedUserId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedUserId)).isEqualTo(USER_ID);

        RefreshTokenSession record = repository.find("hash-1");
        assertThat(record).isNotNull();
        assertThat(record.userId()).isEqualTo(USER_ID);
        assertThat(record.familyId()).isEqualTo("family-1");
        assertThat(record.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void revokeByUserIdShouldRevokeOnlyActiveSessionsForUser() {
        UUID otherUserId = UUID.fromString("00000000-0000-7000-8000-00000000000b");
        Instant expiresAt = Instant.parse("2026-04-21T03:00:00Z");
        repository.store("hash-active-1", USER_ID, "family-1", expiresAt);
        repository.store("hash-active-2", USER_ID, "family-2", expiresAt);
        repository.store("hash-other", otherUserId, "family-3", expiresAt);
        repository.revoke("hash-active-2");

        int revoked = repository.revokeByUserId(USER_ID);

        assertThat(revoked).isEqualTo(1);
        assertThat(repository.find("hash-active-1").revokedAt()).isNotNull();
        assertThat(repository.find("hash-active-2").revokedAt()).isNotNull();
        assertThat(repository.find("hash-other").revokedAt()).isNull();
    }

    @Test
    void storeShouldRejectNewTokenWhenFamilyWasRevoked() {
        Instant expiresAt = Instant.parse("2026-04-21T03:00:00Z");
        repository.store("hash-1", USER_ID, "family-1", expiresAt);
        repository.revokeFamily("family-1");

        assertThatThrownBy(() -> repository.store("hash-2", USER_ID, "family-1", expiresAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh token family");

        assertThat(repository.find("hash-2")).isNull();
    }

    @Test
    void revokeByUserIdShouldMarkFamiliesWithOnlyConsumedRows() {
        Instant expiresAt = Instant.parse("2026-04-21T03:00:00Z");
        repository.store("hash-consumed", USER_ID, "family-consumed", expiresAt);
        repository.consumeActive("hash-consumed", Instant.parse("2026-04-20T03:00:00Z"));

        int revoked = repository.revokeByUserId(USER_ID);

        assertThat(revoked).isZero();
        Integer markerCount = jdbcTemplate.queryForObject(
                "select count(*) from auth_refresh_token_family_revocation where family_id = ?",
                Integer.class,
                "family-consumed"
        );
        assertThat(markerCount).isEqualTo(1);
        assertThatThrownBy(() -> repository.store("hash-rotated", USER_ID, "family-consumed", expiresAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh token family");
        assertThat(repository.find("hash-rotated")).isNull();
    }

    @Test
    void storeShouldUseSingleConditionalInsertForRevokedFamily() {
        Instant expiresAt = Instant.parse("2026-04-21T03:00:00Z");
        jdbcTemplate.update(
                "insert into auth_refresh_token_family_revocation(family_id, revoked_at) values (?, current_timestamp)",
                "family-atomic"
        );

        assertThatThrownBy(() -> repository.store("hash-atomic", USER_ID, "family-atomic", expiresAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh token family");

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from auth_refresh_token where token_hash = ?",
                Integer.class,
                "hash-atomic"
        );
        assertThat(rowCount).isZero();
    }
}
