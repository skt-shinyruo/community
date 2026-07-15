package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MyBatisRefreshTokenRepositoryIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-00000000000a");
    private static final long SECURITY_VERSION_AT_ISSUE = 42L;
    private static final String TOKEN = "plain-refresh-token";
    private static final String REPLACEMENT = "plain-replacement-token";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MyBatisRefreshTokenRepository repository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from auth_refresh_token_family_revocation");
        jdbcTemplate.update("delete from auth_refresh_token");
    }

    @Test
    void storeShouldHashTokenAndPersistBinaryUserIdAndSecurityVersion() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);

        repository.store(TOKEN, USER_ID, "family-1", SECURITY_VERSION_AT_ISSUE, expiresAt);

        byte[] storedUserId = jdbcTemplate.queryForObject(
                "select user_id from auth_refresh_token where token_hash = ?",
                (rs, rowNum) -> rs.getBytes(1),
                sha256Hex(TOKEN)
        );
        Long storedVersion = jdbcTemplate.queryForObject(
                "select security_version from auth_refresh_token where token_hash = ?",
                Long.class,
                sha256Hex(TOKEN)
        );
        assertThat(storedUserId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedUserId)).isEqualTo(USER_ID);
        assertThat(storedVersion).isEqualTo(SECURITY_VERSION_AT_ISSUE);

        RefreshTokenRepository.StoredRefreshToken record = repository.find(TOKEN);
        assertThat(record).isNotNull();
        assertThat(record.userId()).isEqualTo(USER_ID);
        assertThat(record.familyId()).isEqualTo("family-1");
        assertThat(record.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION_AT_ISSUE);
        assertThat(record.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void storeShouldRejectNewTokenWhenFamilyWasRevoked() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        repository.store(TOKEN, USER_ID, "family-1", SECURITY_VERSION_AT_ISSUE, expiresAt);
        repository.revokeFamily("family-1");

        assertThatThrownBy(() -> repository.store(
                REPLACEMENT,
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                expiresAt
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh token family");

        assertThat(repository.find(REPLACEMENT)).isNull();
    }

    @Test
    void storeShouldUseSingleConditionalInsertForRevokedFamily() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update(
                "insert into auth_refresh_token_family_revocation(family_id, revoked_at) values (?, current_timestamp)",
                "family-atomic"
        );

        assertThatThrownBy(() -> repository.store(
                TOKEN,
                USER_ID,
                "family-atomic",
                SECURITY_VERSION_AT_ISSUE,
                expiresAt
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh token family");

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from auth_refresh_token where token_hash = ?",
                Integer.class,
                sha256Hex(TOKEN)
        );
        assertThat(rowCount).isZero();
    }

    @Test
    void beginRollbackAndFinishRotationShouldPreserveSecurityVersion() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        Instant pendingExpiresAt = Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.SECONDS);
        repository.store(TOKEN, USER_ID, "family-rotation", SECURITY_VERSION_AT_ISSUE, expiresAt);

        RefreshTokenRepository.StoredRefreshToken pending = repository.beginRotation(TOKEN, pendingExpiresAt);

        assertThat(pending).isNotNull();
        assertThat(pending.refreshToken()).isEqualTo(TOKEN);
        assertThat(pending.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION_AT_ISSUE);
        assertThat(stateOf(TOKEN)).isEqualTo("PENDING_ROTATION");

        assertThat(repository.rollbackPendingRotation(TOKEN)).isTrue();
        assertThat(stateOf(TOKEN)).isEqualTo("ACTIVE");

        repository.beginRotation(TOKEN, pendingExpiresAt);
        assertThat(repository.finishRotation(
                TOKEN,
                REPLACEMENT,
                USER_ID,
                "family-rotation",
                SECURITY_VERSION_AT_ISSUE,
                expiresAt
        )).isTrue();

        assertThat(stateOf(TOKEN)).isEqualTo("CONSUMED");
        RefreshTokenRepository.StoredRefreshToken replacement = repository.find(REPLACEMENT);
        assertThat(replacement).isNotNull();
        assertThat(replacement.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION_AT_ISSUE);
    }

    @Test
    void beginRotationShouldRecoverExpiredPendingBeforeRetry() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        repository.store(TOKEN, USER_ID, "family-retry", SECURITY_VERSION_AT_ISSUE, expiresAt);
        RefreshTokenRepository.StoredRefreshToken firstPending = repository.beginRotation(
                TOKEN,
                Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS)
        );
        assertThat(firstPending).isNotNull();

        RefreshTokenRepository.StoredRefreshToken retried = repository.beginRotation(
                TOKEN,
                Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.SECONDS)
        );

        assertThat(retried).isNotNull();
        assertThat(retried.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION_AT_ISSUE);
        assertThat(stateOf(TOKEN)).isEqualTo("PENDING_ROTATION");
    }

    private String stateOf(String refreshToken) {
        return jdbcTemplate.queryForObject(
                "select state from auth_refresh_token where token_hash = ?",
                String.class,
                sha256Hex(refreshToken)
        );
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
