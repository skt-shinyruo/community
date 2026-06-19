package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.result.TokenFreshnessResult;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenFreshnessApplicationServiceTest {

    private final UserCredentialQueryApi userCredentialQueryApi = mock(UserCredentialQueryApi.class);
    private final TokenFreshnessApplicationService service = new TokenFreshnessApplicationService(userCredentialQueryApi);

    @Test
    void verifyShouldAcceptFreshActiveToken() {
        UUID userId = uuid(7);
        when(userCredentialQueryApi.getByUserId(userId))
                .thenReturn(new UserCredentialView(userId, "admin", 1, 1, "h1", 123L, true, true));

        TokenFreshnessResult result = service.verify(userId, 123L);

        assertThat(result.status()).isEqualTo(TokenFreshnessResult.Status.ACCEPTED);
    }

    @Test
    void verifyShouldRejectStaleTokenVersion() {
        UUID userId = uuid(7);
        when(userCredentialQueryApi.getByUserId(userId))
                .thenReturn(new UserCredentialView(userId, "admin", 1, 1, "h1", 124L, true, true));

        TokenFreshnessResult result = service.verify(userId, 123L);

        assertThat(result.status()).isEqualTo(TokenFreshnessResult.Status.STALE);
    }

    @Test
    void verifyShouldDenyDisabledOrBannedActor() {
        UUID userId = uuid(7);
        when(userCredentialQueryApi.getByUserId(userId))
                .thenReturn(new UserCredentialView(userId, "admin", 1, 1, "h1", 123L, false, false));

        TokenFreshnessResult result = service.verify(userId, 123L);

        assertThat(result.status()).isEqualTo(TokenFreshnessResult.Status.DENIED);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
