package com.nowcoder.community.auth.service;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import com.nowcoder.community.user.api.model.RefreshTokenSessionView;
import com.nowcoder.community.user.api.query.UserRefreshTokenSessionQueryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbRefreshTokenStoreTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");

    @Mock
    private UserRefreshTokenSessionActionApi refreshTokenSessionActionApi;

    @Mock
    private UserRefreshTokenSessionQueryApi refreshTokenSessionQueryApi;

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
                    UserRefreshTokenSessionActionApi.class, UserRefreshTokenSessionQueryApi.class, JwtProperties.class
            );
            return ctor.newInstance(refreshTokenSessionActionApi, refreshTokenSessionQueryApi, props);
        } catch (NoSuchMethodException ignored) {
            return new DbRefreshTokenStore(refreshTokenSessionActionApi, refreshTokenSessionQueryApi);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void consume_whenConsumeFailsAndTokenWasRevokedOutsideGrace_shouldRevokeFamily() {
        DbRefreshTokenStore store = newStore(10);

        when(refreshTokenSessionActionApi.consume(anyString())).thenReturn(null);

        Instant now = Instant.now();
        lenient().when(refreshTokenSessionQueryApi.find(anyString())).thenReturn(
                new RefreshTokenSessionView(
                        "hash",
                        USER_ID,
                        "family-1",
                        now.plusSeconds(300),
                        now.minusSeconds(60)
                )
        );

        RefreshTokenStore.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isNull();
        verify(refreshTokenSessionActionApi).revokeFamily("family-1");
    }

    @Test
    void consume_whenConsumeFailsAndTokenWasRevokedWithinGrace_shouldNotRevokeFamily() {
        DbRefreshTokenStore store = newStore(10);

        when(refreshTokenSessionActionApi.consume(anyString())).thenReturn(null);

        Instant now = Instant.now();
        lenient().when(refreshTokenSessionQueryApi.find(anyString())).thenReturn(
                new RefreshTokenSessionView(
                        "hash",
                        USER_ID,
                        "family-1",
                        now.plusSeconds(300),
                        now.minusSeconds(3)
                )
        );

        RefreshTokenStore.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isNull();
        verify(refreshTokenSessionActionApi, never()).revokeFamily(anyString());
    }

    @Test
    void consume_shouldUseConfiguredGraceSeconds() {
        DbRefreshTokenStore store = newStore(1);

        when(refreshTokenSessionActionApi.consume(anyString())).thenReturn(null);

        Instant now = Instant.now();
        lenient().when(refreshTokenSessionQueryApi.find(anyString())).thenReturn(
                new RefreshTokenSessionView(
                        "hash",
                        USER_ID,
                        "family-1",
                        now.plusSeconds(300),
                        now.minusSeconds(3)
                )
        );

        RefreshTokenStore.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isNull();
        verify(refreshTokenSessionActionApi).revokeFamily("family-1");
    }

    @Test
    void consume_whenTokenExpired_shouldNotRevokeFamilyEvenIfRevokedLongAgo() {
        DbRefreshTokenStore store = newStore(10);

        when(refreshTokenSessionActionApi.consume(anyString())).thenReturn(null);

        Instant now = Instant.now();
        lenient().when(refreshTokenSessionQueryApi.find(anyString())).thenReturn(
                new RefreshTokenSessionView(
                        "hash",
                        USER_ID,
                        "family-1",
                        now.minusSeconds(1),
                        now.minusSeconds(60)
                )
        );

        RefreshTokenStore.StoredRefreshToken result = store.consume("rt1");

        assertThat(result).isNull();
        verify(refreshTokenSessionActionApi, never()).revokeFamily(anyString());
    }
}
