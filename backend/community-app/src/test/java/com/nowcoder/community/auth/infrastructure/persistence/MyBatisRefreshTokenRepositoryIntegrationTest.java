package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.auth.application.RefreshTokenApplicationService;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.auth.infrastructure.persistence.mapper.RefreshTokenSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private RefreshTokenSessionMapper mapper;

    @Autowired
    private RefreshTokenApplicationService refreshTokenApplicationService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from auth_refresh_token_family_lock");
        jdbcTemplate.update("delete from auth_refresh_token_family_revocation");
        jdbcTemplate.update("delete from auth_refresh_token");
    }

    @Test
    void jdbcRepositoryIsTheRefreshTokenRepository() {
        assertThat(refreshTokenRepository).isSameAs(repository);
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
                "insert into auth_refresh_token_family_revocation(family_id, revoked_at, expires_at) " +
                        "values (?, current_timestamp, ?)",
                "family-atomic",
                expiresAt
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
    void duplicateTokenHashMustNotMoveAnExistingSessionToAnotherFamily() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        repository.store(TOKEN, USER_ID, "family-owner", SECURITY_VERSION_AT_ISSUE, expiresAt);

        assertThatThrownBy(() -> repository.store(
                TOKEN,
                USER_ID,
                "family-collision",
                SECURITY_VERSION_AT_ISSUE,
                expiresAt
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh token 已存在");

        String storedFamily = jdbcTemplate.queryForObject(
                "select family_id from auth_refresh_token where token_hash = ?",
                String.class,
                sha256Hex(TOKEN)
        );
        assertThat(storedFamily).isEqualTo("family-owner");
    }

    @Test
    void beginRollbackAndFinishRotationShouldPreserveSecurityVersion() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        Instant pendingExpiresAt = Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.SECONDS);
        repository.store(TOKEN, USER_ID, "family-rotation", SECURITY_VERSION_AT_ISSUE, expiresAt);
        UUID firstLease = UUID.fromString("00000000-0000-7000-8000-000000000101");

        RefreshTokenRepository.StoredRefreshToken pending = repository.beginRotation(TOKEN, pendingExpiresAt, firstLease);

        assertThat(pending).isNotNull();
        assertThat(pending.refreshToken()).isEqualTo(TOKEN);
        assertThat(pending.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION_AT_ISSUE);
        assertThat(stateOf(TOKEN)).isEqualTo("PENDING_ROTATION");

        assertThat(repository.rollbackPendingRotation(TOKEN, firstLease)).isTrue();
        assertThat(stateOf(TOKEN)).isEqualTo("ACTIVE");

        UUID secondLease = UUID.fromString("00000000-0000-7000-8000-000000000102");
        repository.beginRotation(TOKEN, pendingExpiresAt, secondLease);
        assertThat(repository.finishRotation(
                TOKEN,
                REPLACEMENT,
                USER_ID,
                "family-rotation",
                SECURITY_VERSION_AT_ISSUE,
                expiresAt,
                secondLease
        )).isTrue();

        assertThat(stateOf(TOKEN)).isEqualTo("CONSUMED");
        RefreshTokenRepository.StoredRefreshToken replacement = repository.find(REPLACEMENT);
        assertThat(replacement).isNotNull();
        assertThat(replacement.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION_AT_ISSUE);
    }

    @Test
    void finishPendingRotationCasShouldRejectAnOriginalSessionThatExpiredAfterBegin() {
        Instant originalExpiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        UUID lease = UUID.fromString("00000000-0000-7000-8000-000000000103");
        repository.store(TOKEN, USER_ID, "family-original-expiry", SECURITY_VERSION_AT_ISSUE, originalExpiresAt);
        assertThat(repository.beginRotation(
                TOKEN,
                Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.SECONDS),
                lease
        )).isNotNull();

        Instant expiredAt = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update(
                "update auth_refresh_token set expires_at = ? where token_hash = ?",
                Timestamp.from(expiredAt),
                sha256Hex(TOKEN)
        );

        int updated = mapper.finishPendingRotation(
                sha256Hex(TOKEN),
                USER_ID,
                "family-original-expiry",
                SECURITY_VERSION_AT_ISSUE,
                Instant.now(),
                lease
        );

        assertThat(updated).isZero();
        assertThat(stateOf(TOKEN)).isEqualTo("PENDING_ROTATION");
    }

    @Test
    void beginRotationShouldRecoverExpiredPendingBeforeRetry() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        repository.store(TOKEN, USER_ID, "family-retry", SECURITY_VERSION_AT_ISSUE, expiresAt);
        RefreshTokenRepository.StoredRefreshToken firstPending = repository.beginRotation(
                TOKEN,
                Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS),
                UUID.fromString("00000000-0000-7000-8000-000000000201")
        );
        assertThat(firstPending).isNotNull();

        RefreshTokenRepository.StoredRefreshToken retried = repository.beginRotation(
                TOKEN,
                Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.SECONDS),
                UUID.fromString("00000000-0000-7000-8000-000000000202")
        );

        assertThat(retried).isNotNull();
        assertThat(retried.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION_AT_ISSUE);
        assertThat(stateOf(TOKEN)).isEqualTo("PENDING_ROTATION");
    }

    @Test
    void staleRotationLeaseCannotFinishAfterExpiredLeaseIsReclaimed() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        UUID staleLease = UUID.fromString("00000000-0000-7000-8000-000000000111");
        UUID currentLease = UUID.fromString("00000000-0000-7000-8000-000000000112");
        repository.store(TOKEN, USER_ID, "family-fenced", SECURITY_VERSION_AT_ISSUE, expiresAt);
        repository.beginRotation(TOKEN, Instant.now().minusSeconds(1), staleLease);
        RefreshTokenRepository.StoredRefreshToken current = repository.beginRotation(
                TOKEN,
                Instant.now().plusSeconds(30),
                currentLease
        );

        assertThat(current).isNotNull();
        assertThat(current.rotationLeaseId()).isEqualTo(currentLease);
        assertThat(repository.finishRotation(
                TOKEN,
                REPLACEMENT,
                USER_ID,
                "family-fenced",
                SECURITY_VERSION_AT_ISSUE,
                expiresAt,
                staleLease
        )).isFalse();
        assertThat(repository.find(REPLACEMENT)).isNull();
    }

    @Test
    void logoutCanRevokeFamilyWhilePresentedTokenIsPendingRotation() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        UUID lease = UUID.fromString("00000000-0000-7000-8000-000000000121");
        repository.store(TOKEN, USER_ID, "family-logout", SECURITY_VERSION_AT_ISSUE, expiresAt);
        repository.beginRotation(TOKEN, Instant.now().plusSeconds(30), lease);

        assertThat(repository.revokeFamilyByPresentedToken(TOKEN)).isTrue();
        assertThat(stateOf(TOKEN)).isEqualTo("REVOKED");
        assertThatThrownBy(() -> repository.store(
                REPLACEMENT,
                USER_ID,
                "family-logout",
                SECURITY_VERSION_AT_ISSUE,
                expiresAt
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applicationFinishRotationShouldRollbackReplacementWhenFinalCasFails() {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        UUID lease = UUID.fromString("00000000-0000-7000-8000-000000000131");
        UUID mismatchedUserId = UUID.fromString("00000000-0000-7000-8000-00000000000b");
        repository.store(TOKEN, USER_ID, "family-transaction", SECURITY_VERSION_AT_ISSUE, expiresAt);
        assertThat(repository.beginRotation(TOKEN, Instant.now().plusSeconds(30), lease)).isNotNull();

        assertThatThrownBy(() -> refreshTokenApplicationService.finishRotation(
                TOKEN,
                REPLACEMENT,
                mismatchedUserId,
                "family-transaction",
                SECURITY_VERSION_AT_ISSUE,
                lease
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending rotation");

        assertThat(repository.find(REPLACEMENT)).isNull();
        assertThat(stateOf(TOKEN)).isEqualTo("PENDING_ROTATION");
        Integer storedRows = jdbcTemplate.queryForObject(
                "select count(*) from auth_refresh_token",
                Integer.class
        );
        assertThat(storedRows).isEqualTo(1);
    }

    @Test
    void concurrentRotationAndLogoutShouldSerializeWithoutLeavingAnActiveReplacement() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        repository.store(TOKEN, USER_ID, "family-concurrent-logout", SECURITY_VERSION_AT_ISSUE, expiresAt);
        RefreshTokenRepository.StoredRefreshToken pending = refreshTokenApplicationService.beginRotation(TOKEN);
        assertThat(pending).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            CompletableFuture<Boolean> rotation = CompletableFuture.supplyAsync(() -> {
                await(start);
                return refreshTokenApplicationService.finishRotation(
                        TOKEN,
                        REPLACEMENT,
                        USER_ID,
                        "family-concurrent-logout",
                        SECURITY_VERSION_AT_ISSUE,
                        pending.rotationLeaseId()
                );
            }, executor);
            CompletableFuture<Void> logout = CompletableFuture.runAsync(() -> {
                await(start);
                refreshTokenApplicationService.revokeFamilyByPresentedToken(TOKEN);
            }, executor);

            start.countDown();
            CompletableFuture.allOf(rotation, logout).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(stateOf(TOKEN)).isIn("CONSUMED", "REVOKED");
        assertThat(repository.find(REPLACEMENT)).isNull();
        Integer activeCount = jdbcTemplate.queryForObject(
                "select count(*) from auth_refresh_token "
                        + "where family_id = 'family-concurrent-logout' and state = 'ACTIVE' and revoked_at is null",
                Integer.class
        );
        assertThat(activeCount).isZero();
        Integer markerCount = jdbcTemplate.queryForObject(
                "select count(*) from auth_refresh_token_family_revocation "
                        + "where family_id = 'family-concurrent-logout'",
                Integer.class
        );
        assertThat(markerCount).isOne();
    }

    @Test
    void cleanupShouldDrainMoreThanOneMapperBatchForTokensMarkersAndFamilyLocks() {
        Instant expiredAt = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        Timestamp expiredTimestamp = Timestamp.from(expiredAt);
        byte[] userId = BinaryUuidCodec.toBytes(USER_ID);
        List<Object[]> tokenRows = new ArrayList<>();
        List<Object[]> markerRows = new ArrayList<>();
        List<Object[]> lockRows = new ArrayList<>();
        for (int index = 0; index < 1001; index++) {
            String familyId = "cleanup-family-" + index;
            tokenRows.add(new Object[]{String.format("%064x", index + 1), userId, familyId, expiredTimestamp});
            markerRows.add(new Object[]{familyId, expiredTimestamp, expiredTimestamp});
            lockRows.add(new Object[]{familyId, expiredTimestamp});
        }
        jdbcTemplate.batchUpdate(
                "insert into auth_refresh_token(token_hash, user_id, family_id, security_version, expires_at, state) "
                        + "values (?, ?, ?, 0, ?, 'REVOKED')",
                tokenRows
        );
        jdbcTemplate.batchUpdate(
                "insert into auth_refresh_token_family_revocation(family_id, revoked_at, expires_at) values (?, ?, ?)",
                markerRows
        );
        jdbcTemplate.batchUpdate(
                "insert into auth_refresh_token_family_lock(family_id, retain_until) values (?, ?)",
                lockRows
        );

        int deleted = refreshTokenApplicationService.cleanupExpiredBefore(Instant.now());

        assertThat(deleted).isEqualTo(1001);
        assertThat(countRows("auth_refresh_token")).isZero();
        assertThat(countRows("auth_refresh_token_family_revocation")).isZero();
        assertThat(countRows("auth_refresh_token_family_lock")).isZero();
    }

    private int countRows(String table) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency test did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test interrupted", exception);
        }
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
