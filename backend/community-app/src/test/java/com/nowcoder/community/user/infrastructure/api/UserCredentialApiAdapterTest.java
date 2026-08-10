package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import com.nowcoder.community.user.application.UserCredentialApplicationService;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserCredentialApiAdapterTest {

    @Mock
    private UserCredentialApplicationService applicationService;

    @Test
    void authenticationSubjectShouldExposeOnlyTheOwnerDerivedOpaqueValue() {
        when(applicationService.authenticationSubject("cœur"))
                .thenReturn("utf8mb4_unicode_ci:v1:abc123");
        UserCredentialApiAdapter adapter = new UserCredentialApiAdapter(applicationService);

        UserCredentialQueryApi.AuthenticationSubject subject = adapter.authenticationSubject("cœur");

        assertThat(subject.value()).isEqualTo("utf8mb4_unicode_ci:v1:abc123");
        verify(applicationService).authenticationSubject("cœur");
    }

    @Test
    void getByUserIdShouldReturnNullWhenOwnerReportsMissingUser() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        when(applicationService.getByUserId(userId)).thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));
        UserCredentialApiAdapter adapter = new UserCredentialApiAdapter(applicationService);

        UserCredentialView result = adapter.getByUserId(userId);

        assertThat(result).isNull();
    }

    @Test
    void preparedAuthenticationShouldExposeStableIdAndConsumeCredentialSnapshotOnce() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000008");
        UserAccount account = new UserAccount(
                userId, "coeur", "bcrypt", "", "coeur@example.com",
                0, 1, "h8", Date.from(Instant.EPOCH), null, null, 0L, 7L
        );
        UserCredentialApplicationService.PreparedAuthentication preparation =
                new UserCredentialApplicationService.PreparedAuthentication(account, "bcrypt", true);
        UserCredentialView credential = new UserCredentialView(
                userId, "coeur", "coeur@example.com", 1, 0, "h8", 7L, true, true
        );
        when(applicationService.prepareAuthentication("cœur")).thenReturn(preparation);
        when(applicationService.authenticate(preparation, "secret"))
                .thenReturn(UserAuthenticationResultView.authenticated(credential));
        UserCredentialApiAdapter adapter = new UserCredentialApiAdapter(applicationService);

        UserCredentialQueryApi.AuthenticationChallenge challenge = adapter.prepareAuthentication("cœur");

        assertThat(challenge.userId()).isEqualTo(userId);
        assertThat(challenge.authenticate("secret").authenticated()).isTrue();
        assertThat(challenge.authenticate("secret").authenticated()).isFalse();
        verify(applicationService, times(1)).authenticate(preparation, "secret");
    }
}
