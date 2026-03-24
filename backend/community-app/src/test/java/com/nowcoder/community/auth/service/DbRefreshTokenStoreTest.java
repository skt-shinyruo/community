package com.nowcoder.community.auth.service;

import com.nowcoder.community.infra.security.jwt.JwtProperties;
import com.nowcoder.community.user.session.RefreshTokenSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbRefreshTokenStoreTest {

    @Mock
    private RefreshTokenSessionService refreshTokenSessionService;

    private DbRefreshTokenStore newStore(long graceSeconds) {
        JwtProperties props = new JwtProperties();
        try {
            // Task 4 adds this property; reflection keeps the tests compiling pre-change (TDD).
            JwtProperties.class.getMethod("setRefreshReuseGraceSeconds", long.class).invoke(props, graceSeconds);
        } catch (NoSuchMethodException ignored) {
            // pre-Task4 code: property not available yet
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        try {
            // Task 4 injects JwtProperties into DbRefreshTokenStore.
            Constructor<DbRefreshTokenStore> ctor = DbRefreshTokenStore.class.getConstructor(
                    RefreshTokenSessionService.class, JwtProperties.class
            );
            return ctor.newInstance(refreshTokenSessionService, props);
        } catch (NoSuchMethodException ignored) {
            return new DbRefreshTokenStore(refreshTokenSessionService);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void consume_whenConsumeFailsAndTokenWasRevokedOutsideGrace_shouldRevokeFamily() {
        DbRefreshTokenStore store = newStore(10);

        when(refreshTokenSessionService.consume(anyString())).thenReturn(null);

        Instant now = Instant.now();
        lenient().when(refreshTokenSessionService.find(anyString())).thenReturn(
                new RefreshTokenSessionService.RefreshTokenRecord(
                        "hash",
                        7,
                        "family-1",
                        now.plusSeconds(300),
                        now.minusSeconds(60)
                )
        );

        RefreshTokenStore.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isNull();
        verify(refreshTokenSessionService).revokeFamily("family-1");
    }

    @Test
    void consume_whenConsumeFailsAndTokenWasRevokedWithinGrace_shouldNotRevokeFamily() {
        DbRefreshTokenStore store = newStore(10);

        when(refreshTokenSessionService.consume(anyString())).thenReturn(null);

        Instant now = Instant.now();
        lenient().when(refreshTokenSessionService.find(anyString())).thenReturn(
                new RefreshTokenSessionService.RefreshTokenRecord(
                        "hash",
                        7,
                        "family-1",
                        now.plusSeconds(300),
                        now.minusSeconds(3)
                )
        );

        RefreshTokenStore.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isNull();
        verify(refreshTokenSessionService, never()).revokeFamily(anyString());
    }

    @Test
    void consume_shouldUseConfiguredGraceSeconds() {
        DbRefreshTokenStore store = newStore(1);

        when(refreshTokenSessionService.consume(anyString())).thenReturn(null);

        Instant now = Instant.now();
        lenient().when(refreshTokenSessionService.find(anyString())).thenReturn(
                new RefreshTokenSessionService.RefreshTokenRecord(
                        "hash",
                        7,
                        "family-1",
                        now.plusSeconds(300),
                        now.minusSeconds(3)
                )
        );

        RefreshTokenStore.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isNull();
        verify(refreshTokenSessionService).revokeFamily("family-1");
    }

    @Test
    void consume_whenTokenExpired_shouldNotRevokeFamilyEvenIfRevokedLongAgo() {
        DbRefreshTokenStore store = newStore(10);

        when(refreshTokenSessionService.consume(anyString())).thenReturn(null);

        Instant now = Instant.now();
        lenient().when(refreshTokenSessionService.find(anyString())).thenReturn(
                new RefreshTokenSessionService.RefreshTokenRecord(
                        "hash",
                        7,
                        "family-1",
                        now.minusSeconds(1),
                        now.minusSeconds(60)
                )
        );

        RefreshTokenStore.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isNull();
        verify(refreshTokenSessionService, never()).revokeFamily(anyString());
    }
}
