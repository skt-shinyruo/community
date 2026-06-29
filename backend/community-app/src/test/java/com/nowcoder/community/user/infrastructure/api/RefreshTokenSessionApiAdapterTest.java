package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import com.nowcoder.community.user.api.model.RefreshTokenSessionStateView;
import com.nowcoder.community.user.api.model.RefreshTokenSessionView;
import com.nowcoder.community.user.api.query.UserRefreshTokenSessionQueryApi;
import com.nowcoder.community.user.application.RefreshTokenSessionApplicationService;
import com.nowcoder.community.user.application.result.RefreshTokenSessionResult;
import com.nowcoder.community.user.domain.model.RefreshTokenSessionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenSessionApiAdapterTest {

    private static final String TOKEN_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");
    private static final Instant EXPIRES_AT = Instant.parse("2026-04-21T03:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-04-20T03:00:00Z");
    private static final Instant PENDING_EXPIRES_AT = Instant.parse("2026-04-20T03:00:30Z");

    @Mock
    private RefreshTokenSessionApplicationService applicationService;

    @Test
    void adapterShouldImplementPublishedRefreshTokenSessionApis() {
        RefreshTokenSessionApiAdapter adapter = new RefreshTokenSessionApiAdapter(applicationService);

        assertThat(adapter).isInstanceOf(UserRefreshTokenSessionActionApi.class);
        assertThat(adapter).isInstanceOf(UserRefreshTokenSessionQueryApi.class);
    }

    @Test
    void findAndConsumeShouldDelegateToApplicationServiceAndMapView() {
        RefreshTokenSessionApiAdapter adapter = new RefreshTokenSessionApiAdapter(applicationService);
        RefreshTokenSessionResult result = new RefreshTokenSessionResult(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT, REVOKED_AT);
        when(applicationService.find(TOKEN_HASH)).thenReturn(result);
        when(applicationService.consume(TOKEN_HASH)).thenReturn(result);

        RefreshTokenSessionView found = adapter.find(TOKEN_HASH);
        RefreshTokenSessionView consumed = adapter.consume(TOKEN_HASH);

        assertThat(found).isEqualTo(new RefreshTokenSessionView(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT, REVOKED_AT));
        assertThat(consumed).isEqualTo(new RefreshTokenSessionView(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT, REVOKED_AT));
        verify(applicationService).find(TOKEN_HASH);
        verify(applicationService).consume(TOKEN_HASH);
    }

    @Test
    void actionMethodsShouldDelegateToApplicationService() {
        RefreshTokenSessionApiAdapter adapter = new RefreshTokenSessionApiAdapter(applicationService);

        adapter.store(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT);
        adapter.revoke(TOKEN_HASH);
        adapter.revokeFamily("family-1");
        adapter.revokeByUserId(USER_ID);
        adapter.deleteExpiredBefore(EXPIRES_AT);

        verify(applicationService).store(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT);
        verify(applicationService).revoke(TOKEN_HASH);
        verify(applicationService).revokeFamily("family-1");
        verify(applicationService).revokeByUserId(USER_ID);
        verify(applicationService).deleteExpiredBefore(EXPIRES_AT);
    }

    @Test
    void invalidTokenHashShouldRemainNoOpAtApiBoundary() {
        RefreshTokenSessionApiAdapter adapter = new RefreshTokenSessionApiAdapter(applicationService);

        assertThat(adapter.find("bad-token")).isNull();
        assertThat(adapter.consume("bad-token")).isNull();
        adapter.store("bad-token", USER_ID, "family-1", EXPIRES_AT);
        adapter.revoke("bad-token");

        verify(applicationService, never()).find("bad-token");
        verify(applicationService, never()).consume("bad-token");
        verify(applicationService, never()).store("bad-token", USER_ID, "family-1", EXPIRES_AT);
        verify(applicationService, never()).revoke("bad-token");
    }

    @Test
    void rotationMethodsShouldDelegateToApplicationServiceAndMapState() {
        RefreshTokenSessionApiAdapter adapter = new RefreshTokenSessionApiAdapter(applicationService);
        RefreshTokenSessionResult result = new RefreshTokenSessionResult(
                TOKEN_HASH,
                USER_ID,
                "family-1",
                EXPIRES_AT,
                null,
                RefreshTokenSessionState.PENDING_ROTATION,
                PENDING_EXPIRES_AT
        );
        when(applicationService.beginRotation(TOKEN_HASH, PENDING_EXPIRES_AT)).thenReturn(result);
        when(applicationService.finishRotation(TOKEN_HASH, "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd", USER_ID, "family-1", EXPIRES_AT))
                .thenReturn(true);
        when(applicationService.rollbackPendingRotation(TOKEN_HASH)).thenReturn(true);

        RefreshTokenSessionView pending = adapter.beginRotation(TOKEN_HASH, PENDING_EXPIRES_AT);

        assertThat(pending).isEqualTo(new RefreshTokenSessionView(
                TOKEN_HASH,
                USER_ID,
                "family-1",
                EXPIRES_AT,
                null,
                RefreshTokenSessionStateView.PENDING_ROTATION,
                PENDING_EXPIRES_AT
        ));
        assertThat(adapter.finishRotation(TOKEN_HASH, "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd", USER_ID, "family-1", EXPIRES_AT)).isTrue();
        assertThat(adapter.rollbackPendingRotation(TOKEN_HASH)).isTrue();
    }
}
