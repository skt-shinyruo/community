package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserCredentialActionApi;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import com.nowcoder.community.user.application.UserCredentialApplicationService;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class UserCredentialApiAdapter implements UserCredentialQueryApi, UserCredentialActionApi {

    private final UserCredentialApplicationService applicationService;

    public UserCredentialApiAdapter(UserCredentialApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
    }

    @Override
    public AuthenticationChallenge prepareAuthentication(String username) {
        return new PreparedAuthenticationChallengeApiAdapter(applicationService.prepareAuthentication(username));
    }

    @Override
    public AuthenticationSubject authenticationSubject(String username) {
        return new AuthenticationSubject(applicationService.authenticationSubject(username));
    }

    @Override
    public UserCredentialView getByUserId(UUID userId) {
        try {
            return applicationService.getByUserId(userId);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
                return null;
            }
            throw ex;
        }
    }

    @Override
    public UserCredentialView findByEmailOrNull(String email) {
        return applicationService.findByEmailOrNull(email);
    }

    @Override
    public List<String> authoritiesOf(UserCredentialView user) {
        return applicationService.authoritiesOf(user);
    }

    @Override
    public void validatePasswordPolicy(String newPassword) {
        applicationService.validatePasswordPolicy(newPassword);
    }

    @Override
    public boolean updatePasswordIfSecurityVersion(
            UUID userId,
            String newPassword,
            long expectedSecurityVersion
    ) {
        return applicationService.updatePasswordIfSecurityVersion(userId, newPassword, expectedSecurityVersion);
    }

    private final class PreparedAuthenticationChallengeApiAdapter implements AuthenticationChallenge {

        private final UUID userId;
        private final AtomicReference<UserCredentialApplicationService.PreparedAuthentication> preparation;

        private PreparedAuthenticationChallengeApiAdapter(
                UserCredentialApplicationService.PreparedAuthentication preparation
        ) {
            this.userId = preparation == null || preparation.user() == null
                    ? null
                    : preparation.user().id();
            this.preparation = new AtomicReference<>(preparation);
        }

        @Override
        public UUID userId() {
            return userId;
        }

        @Override
        public UserAuthenticationResultView authenticate(String password) {
            UserCredentialApplicationService.PreparedAuthentication current = preparation.getAndSet(null);
            if (current == null) {
                return UserAuthenticationResultView.invalidCredentials();
            }
            UserAuthenticationResultView result = applicationService.authenticate(current, password);
            return result == null ? UserAuthenticationResultView.invalidCredentials() : result;
        }
    }
}
