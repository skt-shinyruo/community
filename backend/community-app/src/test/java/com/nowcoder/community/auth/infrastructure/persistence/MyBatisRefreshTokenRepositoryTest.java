package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.RefreshTokenSessionState;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.infrastructure.persistence.dataobject.RefreshTokenSessionDataObject;
import com.nowcoder.community.auth.infrastructure.persistence.mapper.RefreshTokenSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper);
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
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper);
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
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper);
        when(mapper.selectByTokenHash(anyString())).thenReturn(null);

        assertThat(repository.consume("rt1")).isNull();

        verify(mapper, never()).upsertFamilyRevocation(anyString());
        verify(mapper, never()).revokeFamilyTokens(anyString());
    }

    @Test
    void findRevokedWhenTokenIsActiveShouldReturnNull() {
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper);
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
        MyBatisRefreshTokenRepository repository = new MyBatisRefreshTokenRepository(mapper);
        Instant pendingExpiresAt = Instant.parse("2026-04-20T03:00:30Z");
        Instant replacementExpiresAt = Instant.parse("2026-04-21T03:00:00Z");
        String oldRefreshHash = sha256Hex("old-refresh");
        String newRefreshHash = sha256Hex("new-refresh");
        when(mapper.storeIfFamilyActive(anyString(), any(UUID.class), anyString(), anyLong(), any(Instant.class)))
                .thenReturn(1);
        when(mapper.beginRotation(anyString(), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(mapper.selectByTokenHash(oldRefreshHash)).thenReturn(row(
                oldRefreshHash,
                replacementExpiresAt,
                null,
                RefreshTokenSessionState.PENDING_ROTATION,
                pendingExpiresAt
        ));
        when(mapper.finishPendingRotation(
                eq(oldRefreshHash),
                eq(USER_ID),
                eq("family-1"),
                eq(SECURITY_VERSION_AT_ISSUE),
                any(Instant.class)
        )).thenReturn(1);
        when(mapper.rollbackPendingRotation(oldRefreshHash)).thenReturn(1);

        repository.store(
                "old-refresh",
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt
        );
        RefreshTokenRepository.StoredRefreshToken rotation = repository.beginRotation(
                "old-refresh",
                pendingExpiresAt
        );

        assertThat(rotation.familyId()).isEqualTo("family-1");
        assertThat(rotation.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION_AT_ISSUE);
        assertThat(repository.finishRotation(
                "old-refresh",
                "new-refresh",
                USER_ID,
                "family-1",
                SECURITY_VERSION_AT_ISSUE,
                replacementExpiresAt
        )).isTrue();
        assertThat(repository.rollbackPendingRotation("old-refresh")).isTrue();
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

    private RefreshTokenSessionDataObject row(
            String tokenHash,
            Instant expiresAt,
            Instant revokedAt,
            RefreshTokenSessionState state,
            Instant pendingExpiresAt
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
