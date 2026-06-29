package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.application.port.RefreshTokenSessionPort;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbRefreshTokenRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");

    @Mock
    private RefreshTokenSessionPort refreshTokenSessionPort;

    private DbRefreshTokenRepository newStore() {
        return new DbRefreshTokenRepository(refreshTokenSessionPort);
    }

    @Test
    void consumeWhenActiveTokenExistsShouldReturnStoredToken() {
        DbRefreshTokenRepository store = newStore();

        Instant expiresAt = Instant.now().plusSeconds(300);
        when(refreshTokenSessionPort.consume(anyString())).thenReturn(
                new RefreshTokenSessionPort.RefreshTokenSession(
                        "hash",
                        USER_ID,
                        "family-1",
                        expiresAt,
                        null
                )
        );

        RefreshTokenRepository.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isEqualTo(new RefreshTokenRepository.StoredRefreshToken("rt1", USER_ID, "family-1", expiresAt));
    }

    @Test
    void findRevokedShouldReturnRevokedMetadataWithoutRevokingFamily() {
        DbRefreshTokenRepository store = newStore();

        Instant now = Instant.now();
        when(refreshTokenSessionPort.find(anyString())).thenReturn(
                new RefreshTokenSessionPort.RefreshTokenSession(
                        "hash",
                        USER_ID,
                        "family-1",
                        now.plusSeconds(300),
                        now.minusSeconds(3)
                )
        );

        RefreshTokenRepository.RevokedRefreshToken result = store.findRevoked("rt1");

        assertThat(result).isEqualTo(new RefreshTokenRepository.RevokedRefreshToken(
                "rt1",
                USER_ID,
                "family-1",
                now.plusSeconds(300),
                now.minusSeconds(3)
        ));
        verify(refreshTokenSessionPort, never()).revokeFamily(anyString());
    }

    @Test
    void consumeWhenTokenWasAlreadyRevokedShouldNotDecideReuseOrRevokeFamily() {
        DbRefreshTokenRepository store = newStore();

        when(refreshTokenSessionPort.consume(anyString())).thenReturn(null);

        RefreshTokenRepository.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isNull();
        verify(refreshTokenSessionPort, never()).find(anyString());
        verify(refreshTokenSessionPort, never()).revokeFamily(anyString());
    }

    @Test
    void findRevokedWhenTokenIsActiveShouldReturnNull() {
        DbRefreshTokenRepository store = newStore();

        when(refreshTokenSessionPort.find(anyString())).thenReturn(
                new RefreshTokenSessionPort.RefreshTokenSession(
                        "hash",
                        USER_ID,
                        "family-1",
                        Instant.now().plusSeconds(300),
                        null
                )
        );

        RefreshTokenRepository.RevokedRefreshToken result = store.findRevoked("rt1");

        assertThat(result).isNull();
    }

    @Test
    void beginFinishAndRollbackRotationShouldHashPresentedTokens() {
        DbRefreshTokenRepository store = newStore();
        Instant pendingExpiresAt = Instant.parse("2026-04-20T03:00:30Z");
        Instant replacementExpiresAt = Instant.parse("2026-04-21T03:00:00Z");
        String oldRefreshHash = sha256Hex("old-refresh");
        String newRefreshHash = sha256Hex("new-refresh");
        RefreshTokenSessionPort.RefreshTokenSession pending = new RefreshTokenSessionPort.RefreshTokenSession(
                oldRefreshHash,
                USER_ID,
                "family-1",
                replacementExpiresAt,
                null,
                RefreshTokenSessionPort.RefreshTokenSessionState.PENDING_ROTATION,
                pendingExpiresAt
        );
        when(refreshTokenSessionPort.beginRotation(oldRefreshHash, pendingExpiresAt)).thenReturn(pending);
        when(refreshTokenSessionPort.finishRotation(
                oldRefreshHash,
                newRefreshHash,
                USER_ID,
                "family-1",
                replacementExpiresAt
        )).thenReturn(true);
        when(refreshTokenSessionPort.rollbackPendingRotation(oldRefreshHash)).thenReturn(true);

        assertThat(store.beginRotation("old-refresh", pendingExpiresAt).familyId()).isEqualTo("family-1");
        assertThat(store.finishRotation("old-refresh", "new-refresh", USER_ID, "family-1", replacementExpiresAt)).isTrue();
        assertThat(store.rollbackPendingRotation("old-refresh")).isTrue();
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
