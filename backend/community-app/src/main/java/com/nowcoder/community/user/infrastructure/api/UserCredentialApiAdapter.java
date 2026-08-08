package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserCredentialActionApi;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import com.nowcoder.community.user.application.UserCredentialApplicationService;
import com.nowcoder.community.user.application.result.UserAuthenticationResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class UserCredentialApiAdapter implements UserCredentialQueryApi, UserCredentialActionApi {

    private final UserCredentialApplicationService applicationService;

    public UserCredentialApiAdapter(UserCredentialApplicationService applicationService) {
        this.applicationService = applicationService;
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
            return toCredentialView(applicationService.getByUserId(userId));
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
                return null;
            }
            throw ex;
        }
    }

    @Override
    public UserCredentialView findByEmailOrNull(String email) {
        return toCredentialView(applicationService.findByEmailOrNull(email));
    }

    @Override
    public List<String> authoritiesOf(UserCredentialView user) {
        return applicationService.authoritiesOf(toCredentialResult(user));
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

    private UserAuthenticationResultView toAuthenticationView(UserAuthenticationResult result) {
        if (result == null) {
            return UserAuthenticationResultView.invalidCredentials();
        }
        UserCredentialView user = toCredentialView(result.user());
        if (result.failure() == UserAuthenticationResult.Failure.USER_DISABLED) {
            return UserAuthenticationResultView.userDisabled(user);
        }
        if (result.failure() == UserAuthenticationResult.Failure.INVALID_CREDENTIALS) {
            return UserAuthenticationResultView.invalidCredentials();
        }
        return UserAuthenticationResultView.authenticated(user);
    }

    private UserCredentialView toCredentialView(UserCredentialResult result) {
        if (result == null) {
            return null;
        }
        return new UserCredentialView(
                result.userId(),
                result.username(),
                result.email(),
                result.status(),
                result.type(),
                result.headerUrl(),
                result.securityVersion(),
                result.loginAllowed(),
                result.refreshAllowed()
        );
    }

    private UserCredentialResult toCredentialResult(UserCredentialView user) {
        if (user == null) {
            return null;
        }
        return new UserCredentialResult(
                user.userId(),
                user.username(),
                user.email(),
                user.status(),
                user.type(),
                user.headerUrl(),
                user.securityVersion(),
                user.loginAllowed(),
                user.refreshAllowed()
        );
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
            return toAuthenticationView(applicationService.authenticate(current, password));
        }
    }
}
