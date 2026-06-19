package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.application.port.RefreshTokenSessionPort;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
