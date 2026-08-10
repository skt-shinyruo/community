package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.RefreshTokenSessionState;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.infrastructure.persistence.dataobject.RefreshTokenSessionDataObject;
import com.nowcoder.community.auth.infrastructure.persistence.mapper.RefreshTokenSessionMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisRefreshTokenRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");
    private static final long SECURITY_VERSION_AT_ISSUE = 42L;

    @Mock
    private RefreshTokenSessionMapper mapper;

    @Test
    void consumeWhenActiveTokenExistsShouldReturnStoredTokenWithSecurityVersion() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        Instant expiresAt = Instant.now().plusSeconds(300);
        String tokenHash = sha256Hex("rt1");
        when(mapper.selectByTokenHash(tokenHash)).thenReturn(row(
                tokenHash,
                expiresAt,
                null,
                RefreshTokenSessionState.ACTIVE,
                null
        ));
        when(mapper.consumeActive(anyString(), any(Instant.class))).thenReturn(1);

        RefreshTokenRepository.StoredRefreshToken result = repository.consume("rt1");

        assertThat(result).isEqualTo(new RefreshTokenRepository.StoredRefreshToken(
                "rt1",
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                expiresAt
        ));
    }

    @Test
    void findRevokedShouldReturnRevokedMetadataWithoutRevokingFamily() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        Instant now = Instant.now();
        String tokenHash = sha256Hex("rt1");
        when(mapper.selectByTokenHash(tokenHash)).thenReturn(row(
                tokenHash,
                now.plusSeconds(300),
                now.minusSeconds(3),
                RefreshTokenSessionState.REVOKED,
                null
        ));

        RefreshTokenRepository.RevokedRefreshToken result = repository.findRevoked("rt1");

        assertThat(result).isEqualTo(new RefreshTokenRepository.RevokedRefreshToken(
                "rt1",
                USER_ID,
                "family-1",
                now.plusSeconds(300),
                now.minusSeconds(3)
        ));
        verify(mapper, never()).upsertFamilyRevocation(anyString());
    }

    @Test
    void consumeWhenTokenWasAlreadyRevokedShouldNotDecideReuseOrRevokeFamily() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        when(mapper.selectByTokenHash(anyString())).thenReturn(null);

        assertThat(repository.consume("rt1")).isNull();

        verify(mapper, never()).upsertFamilyRevocation(anyString());
        verify(mapper, never()).revokeFamilyTokens(anyString());
    }

    @Test
    void findRevokedWhenTokenIsActiveShouldReturnNull() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        String tokenHash = sha256Hex("rt1");
        when(mapper.selectByTokenHash(tokenHash)).thenReturn(row(
                tokenHash,
                Instant.now().plusSeconds(300),
                null,
                RefreshTokenSessionState.ACTIVE,
                null
        ));

        assertThat(repository.findRevoked("rt1")).isNull();
    }

    @Test
    void storeBeginFinishAndRollbackShouldPreserveSecurityVersionAndHashPresentedTokens() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        Instant pendingExpiresAt = Instant.now().plusSeconds(30);
        Instant replacementExpiresAt = Instant.now().plusSeconds(3600);
        UUID rotationLeaseId = UUID.fromString("00000000-0000-7000-8000-000000000099");
        String oldRefreshHash = sha256Hex("old-refresh");
        String newRefreshHash = sha256Hex("new-refresh");
        when(mapper.storeIfFamilyActive(anyString(), any(UUID.class), anyString(), anyLong(), any(Instant.class)))
                .thenReturn(1);
        when(mapper.beginRotation(anyString(), any(Instant.class), any(Instant.class), eq(rotationLeaseId)))
                .thenReturn(1);
        when(mapper.selectByTokenHash(oldRefreshHash)).thenReturn(row(
                oldRefreshHash,
                replacementExpiresAt,
                null,
                RefreshTokenSessionState.PENDING_ROTATION,
                pendingExpiresAt,
                rotationLeaseId
        ));
        when(mapper.selectByTokenHashForUpdate(oldRefreshHash)).thenReturn(row(
                oldRefreshHash,
                replacementExpiresAt,
                null,
                RefreshTokenSessionState.PENDING_ROTATION,
                pendingExpiresAt,
                rotationLeaseId
        ));
        when(mapper.selectFamilyLockForUpdate("family-1")).thenReturn("family-1");
        when(mapper.finishPendingRotation(
                eq(oldRefreshHash),
                eq(USER_ID),
                eq("family-1"),
                eq(SECURITY_VERSION_AT_ISSUE),
                any(Instant.class),
                eq(rotationLeaseId)
        )).thenReturn(1);
        when(mapper.rollbackPendingRotation(oldRefreshHash, rotationLeaseId)).thenReturn(1);

        repository.store(
                "old-refresh",
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt
        );
        RefreshTokenRepository.StoredRefreshToken rotation = repository.beginRotation(
                "old-refresh",
                pendingExpiresAt,
                rotationLeaseId
        );

        assertThat(rotation.familyId()).isEqualTo("family-1");
        assertThat(rotation.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION_AT_ISSUE);
        assertThat(repository.finishRotation(
                "old-refresh",
                "new-refresh",
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt,
                rotationLeaseId
        )).isTrue();
        assertThat(repository.rollbackPendingRotation("old-refresh", rotationLeaseId)).isTrue();
        verify(mapper).storeIfFamilyActive(
                oldRefreshHash,
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt
        );
        verify(mapper).storeIfFamilyActive(
                newRefreshHash,
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt
        );
    }

    @Test
    void storeShouldLockFamilyBeforeWritingTokenRow() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        Instant expiresAt = Instant.now().plusSeconds(3600);
        String tokenHash = sha256Hex("new-token");
        when(mapper.selectFamilyLockForUpdate("family-store")).thenReturn("family-store");
        when(mapper.storeIfFamilyActive(tokenHash, USER_ID, "family-store", SECURITY_VERSION_AT_ISSUE, expiresAt))
                .thenReturn(1);

        repository.store("new-token", USER_ID, "family-store", SECURITY_VERSION_AT_ISSUE, expiresAt);

        var ordered = inOrder(mapper);
        ordered.verify(mapper).ensureFamilyLock("family-store", expiresAt);
        ordered.verify(mapper).selectFamilyLockForUpdate("family-store");
        ordered.verify(mapper).storeIfFamilyActive(
                tokenHash,
                USER_ID,
                "family-store",
                SECURITY_VERSION_AT_ISSUE,
                expiresAt
        );
    }

    @Test
    void beginRotationShouldLockFamilyBeforeLockingAndUpdatingTokenRow() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        String tokenHash = sha256Hex("begin-token");
        Instant expiresAt = Instant.now().plusSeconds(3600);
        Instant pendingExpiresAt = Instant.now().plusSeconds(30);
        UUID leaseId = UUID.fromString("00000000-0000-7000-8000-000000000201");
        RefreshTokenSessionDataObject active = row(
                tokenHash,
                expiresAt,
                null,
                RefreshTokenSessionState.ACTIVE,
                null
        );
        RefreshTokenSessionDataObject pending = row(
                tokenHash,
                expiresAt,
                null,
                RefreshTokenSessionState.PENDING_ROTATION,
                pendingExpiresAt,
                leaseId
        );
        when(mapper.selectByTokenHash(tokenHash)).thenReturn(active, pending);
        when(mapper.selectFamilyLockForUpdate("family-1")).thenReturn("family-1");
        when(mapper.selectByTokenHashForUpdate(tokenHash)).thenReturn(active);
        when(mapper.beginRotation(eq(tokenHash), eq(pendingExpiresAt), any(Instant.class), eq(leaseId)))
                .thenReturn(1);

        assertThat(repository.beginRotation("begin-token", pendingExpiresAt, leaseId)).isNotNull();

        var ordered = inOrder(mapper);
        ordered.verify(mapper).selectByTokenHash(tokenHash);
        ordered.verify(mapper).ensureFamilyLock(eq("family-1"), any(Instant.class));
        ordered.verify(mapper).selectFamilyLockForUpdate("family-1");
        ordered.verify(mapper).selectByTokenHashForUpdate(tokenHash);
        ordered.verify(mapper).recoverExpiredPending(eq(tokenHash), any(Instant.class));
        ordered.verify(mapper).beginRotation(eq(tokenHash), eq(pendingExpiresAt), any(Instant.class), eq(leaseId));
    }

    @Test
    void finishRotationShouldLockFamilyBeforePendingAndReplacementTokenRows() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        String pendingHash = sha256Hex("pending-token");
        String replacementHash = sha256Hex("replacement-token");
        Instant replacementExpiresAt = Instant.now().plusSeconds(3600);
        UUID leaseId = UUID.fromString("00000000-0000-7000-8000-000000000202");
        RefreshTokenSessionDataObject pending = row(
                pendingHash,
                replacementExpiresAt,
                null,
                RefreshTokenSessionState.PENDING_ROTATION,
                Instant.now().plusSeconds(30),
                leaseId
        );
        when(mapper.selectByTokenHash(pendingHash)).thenReturn(pending);
        when(mapper.selectFamilyLockForUpdate("family-1")).thenReturn("family-1");
        when(mapper.selectByTokenHashForUpdate(pendingHash)).thenReturn(pending);
        when(mapper.storeIfFamilyActive(
                replacementHash,
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt
        )).thenReturn(1);
        when(mapper.finishPendingRotation(
                eq(pendingHash),
                eq(USER_ID),
                eq("family-1"),
                eq(SECURITY_VERSION_AT_ISSUE),
                any(Instant.class),
                eq(leaseId)
        )).thenReturn(1);

        assertThat(repository.finishRotation(
                "pending-token",
                "replacement-token",
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt,
                leaseId
        )).isTrue();

        var ordered = inOrder(mapper);
        ordered.verify(mapper).selectByTokenHash(pendingHash);
        ordered.verify(mapper).ensureFamilyLock("family-1", replacementExpiresAt);
        ordered.verify(mapper).selectFamilyLockForUpdate("family-1");
        ordered.verify(mapper).selectByTokenHashForUpdate(pendingHash);
        ordered.verify(mapper).storeIfFamilyActive(
                replacementHash,
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt
        );
        ordered.verify(mapper).finishPendingRotation(
                eq(pendingHash),
                eq(USER_ID),
                eq("family-1"),
                eq(SECURITY_VERSION_AT_ISSUE),
                any(Instant.class),
                eq(leaseId)
        );
    }

    @Test
    void finishRotationShouldRejectAnExpiredOriginalTokenBeforeStoringReplacement() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        String pendingHash = sha256Hex("expired-pending-token");
        UUID leaseId = UUID.fromString("00000000-0000-7000-8000-000000000203");
        RefreshTokenSessionDataObject expiredPending = row(
                pendingHash,
                Instant.now().minusSeconds(1),
                null,
                RefreshTokenSessionState.PENDING_ROTATION,
                Instant.now().plusSeconds(30),
                leaseId
        );
        when(mapper.selectByTokenHash(pendingHash)).thenReturn(expiredPending);
        when(mapper.selectFamilyLockForUpdate("family-1")).thenReturn("family-1");
        when(mapper.selectByTokenHashForUpdate(pendingHash)).thenReturn(expiredPending);

        assertThat(repository.finishRotation(
                "expired-pending-token",
                "replacement-token",
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                Instant.now().plusSeconds(3600),
                leaseId
        )).isFalse();

        verify(mapper, never()).storeIfFamilyActive(
                anyString(),
                any(UUID.class),
                anyString(),
                anyLong(),
                any(Instant.class)
        );
        verify(mapper, never()).finishPendingRotation(
                anyString(),
                any(UUID.class),
                anyString(),
                anyLong(),
                any(Instant.class),
                any(UUID.class)
        );
    }

    @Test
    void finishRotationShouldCaptureCasTimeAfterPendingRowLockReturns() throws InterruptedException {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        String pendingHash = sha256Hex("time-ordered-pending-token");
        Instant replacementExpiresAt = Instant.now().plusSeconds(3600);
        UUID leaseId = UUID.fromString("00000000-0000-7000-8000-000000000204");
        RefreshTokenSessionDataObject pending = row(
                pendingHash,
                replacementExpiresAt,
                null,
                RefreshTokenSessionState.PENDING_ROTATION,
                Instant.now().plusSeconds(30),
                leaseId
        );
        AtomicReference<Instant> pendingLockReturnedAt = new AtomicReference<>();
        when(mapper.selectByTokenHash(pendingHash)).thenReturn(pending);
        when(mapper.selectFamilyLockForUpdate("family-1")).thenReturn("family-1");
        when(mapper.selectByTokenHashForUpdate(pendingHash)).thenAnswer(invocation -> {
            Thread.sleep(25);
            pendingLockReturnedAt.set(Instant.now());
            return pending;
        });
        when(mapper.storeIfFamilyActive(
                sha256Hex("time-ordered-replacement-token"),
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt
        )).thenReturn(1);
        when(mapper.finishPendingRotation(
                eq(pendingHash),
                eq(USER_ID),
                eq("family-1"),
                eq(SECURITY_VERSION_AT_ISSUE),
                any(Instant.class),
                eq(leaseId)
        )).thenReturn(1);

        assertThat(repository.finishRotation(
                "time-ordered-pending-token",
                "time-ordered-replacement-token",
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt,
                leaseId
        )).isTrue();

        ArgumentCaptor<Instant> casNow = ArgumentCaptor.forClass(Instant.class);
        verify(mapper).finishPendingRotation(
                eq(pendingHash),
                eq(USER_ID),
                eq("family-1"),
                eq(SECURITY_VERSION_AT_ISSUE),
                casNow.capture(),
                eq(leaseId)
        );
        assertThat(casNow.getValue()).isAfterOrEqualTo(pendingLockReturnedAt.get());
    }

    @Test
    void revokeFamilyShouldLockFamilyBeforeRevocationMarkerAndTokenRows() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        when(mapper.selectFamilyLockForUpdate("family-revoke")).thenReturn("family-revoke");

        repository.revokeFamily("family-revoke");

        var ordered = inOrder(mapper);
        ordered.verify(mapper).ensureFamilyLock(eq("family-revoke"), any(Instant.class));
        ordered.verify(mapper).selectFamilyLockForUpdate("family-revoke");
        ordered.verify(mapper).upsertFamilyRevocation("family-revoke");
        ordered.verify(mapper).revokeFamilyTokens("family-revoke");
    }

    @Test
    void revokeByPresentedTokenShouldLockFamilyBeforeTokenAndRevocationRows() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        String tokenHash = sha256Hex("rt-lock-order");
        Instant expiresAt = Instant.now().plusSeconds(300);
        RefreshTokenSessionDataObject token = row(
                tokenHash,
                expiresAt,
                null,
                RefreshTokenSessionState.PENDING_ROTATION,
                Instant.now().plusSeconds(30)
        );
        when(mapper.selectByTokenHash(tokenHash)).thenReturn(token);
        when(mapper.selectFamilyLockForUpdate("family-1")).thenReturn("family-1");
        when(mapper.selectByTokenHashForUpdate(tokenHash)).thenReturn(token);

        assertThat(repository.revokeFamilyByPresentedToken("rt-lock-order")).isTrue();

        var ordered = inOrder(mapper);
        ordered.verify(mapper).selectByTokenHash(tokenHash);
        ordered.verify(mapper).ensureFamilyLock(eq("family-1"), any(Instant.class));
        ordered.verify(mapper).selectFamilyLockForUpdate("family-1");
        ordered.verify(mapper).selectByTokenHashForUpdate(tokenHash);
        ordered.verify(mapper).upsertFamilyRevocation("family-1");
        ordered.verify(mapper).revokeFamilyTokens("family-1");
    }

    @Test
    void cleanupShouldDrainEveryTableInBatchesAndStopAfterShortBatch() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        Instant cutoff = Instant.now();
        when(mapper.deleteExpiredBefore(cutoff)).thenReturn(500, 500, 17);
        when(mapper.deleteExpiredFamilyRevocationsBefore(cutoff)).thenReturn(500, 3);
        when(mapper.deleteExpiredFamilyLocksBefore(cutoff)).thenReturn(2);

        assertThat(repository.deleteExpiredBefore(cutoff)).isEqualTo(1017);

        verify(mapper, times(3)).deleteExpiredBefore(cutoff);
        verify(mapper, times(2)).deleteExpiredFamilyRevocationsBefore(cutoff);
        verify(mapper).deleteExpiredFamilyLocksBefore(cutoff);
    }

    @Test
    void cleanupShouldHaveAHardProgressBoundWhenEveryBatchStaysFull() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper, Clock.systemUTC());
        Instant cutoff = Instant.now();
        when(mapper.deleteExpiredBefore(cutoff)).thenReturn(500);
        when(mapper.deleteExpiredFamilyRevocationsBefore(cutoff)).thenReturn(0);
        when(mapper.deleteExpiredFamilyLocksBefore(cutoff)).thenReturn(0);

        assertThat(repository.deleteExpiredBefore(cutoff)).isEqualTo(100_000);

        verify(mapper, times(200)).deleteExpiredBefore(cutoff);
    }

    private RefreshTokenSessionDataObject row(
            String tokenHash,
            Instant expiresAt,
            Instant revokedAt,
            RefreshTokenSessionState state,
            Instant pendingExpiresAt
    ) {
        return row(tokenHash, expiresAt, revokedAt, state, pendingExpiresAt, null);
    }

    private RefreshTokenSessionDataObject row(
            String tokenHash,
            Instant expiresAt,
            Instant revokedAt,
            RefreshTokenSessionState state,
            Instant pendingExpiresAt,
            UUID rotationLeaseId
    ) {
        RefreshTokenSessionDataObject row = new RefreshTokenSessionDataObject();
        row.setTokenHash(tokenHash);
        row.setUserId(USER_ID);
        row.setFamilyId("family-1");
        row.setSecurityVersionAtIssue(SECURITY_VERSION_AT_ISSUE);
        row.setExpiresAt(expiresAt);
        row.setRevokedAt(revokedAt);
        row.setState(state);
        row.setPendingExpiresAt(pendingExpiresAt);
        row.setRotationLeaseId(rotationLeaseId);
        return row;
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
