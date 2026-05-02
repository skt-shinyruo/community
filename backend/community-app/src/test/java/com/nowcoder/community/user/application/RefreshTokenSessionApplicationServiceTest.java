package com.nowcoder.community.user.application;

import com.nowcoder.community.user.application.result.RefreshTokenSessionResult;
import com.nowcoder.community.user.domain.model.RefreshTokenSession;
import com.nowcoder.community.user.domain.repository.RefreshTokenSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenSessionApplicationServiceTest {

    private static final String TOKEN_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");
    private static final Instant EXPIRES_AT = Instant.parse("2026-04-21T03:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-04-20T03:00:00Z");

    @Mock
    private RefreshTokenSessionRepository repository;

    @Test
    void findAndConsumeShouldMapDomainSessionToResult() {
        RefreshTokenSessionApplicationService service = new RefreshTokenSessionApplicationService(repository);
        RefreshTokenSession session = new RefreshTokenSession(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT, REVOKED_AT);
        when(repository.find(TOKEN_HASH)).thenReturn(session);
        when(repository.consumeActive(TOKEN_HASH)).thenReturn(session);

        RefreshTokenSessionResult found = service.find(TOKEN_HASH);
        RefreshTokenSessionResult consumed = service.consume(TOKEN_HASH);

        assertThat(found).isEqualTo(new RefreshTokenSessionResult(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT, REVOKED_AT));
        assertThat(consumed).isEqualTo(new RefreshTokenSessionResult(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT, REVOKED_AT));
    }

    @Test
    void actionMethodsShouldDelegateToRepository() {
        RefreshTokenSessionApplicationService service = new RefreshTokenSessionApplicationService(repository);

        service.store(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT);
        service.revoke(TOKEN_HASH);
        service.revokeFamily("family-1");
        service.revokeByUserId(USER_ID);
        service.deleteExpiredBefore(EXPIRES_AT);

        verify(repository).store(TOKEN_HASH, USER_ID, "family-1", EXPIRES_AT);
        verify(repository).revoke(TOKEN_HASH);
        verify(repository).revokeFamily("family-1");
        verify(repository).revokeByUserId(USER_ID);
        verify(repository).deleteExpiredBefore(EXPIRES_AT);
    }
}
