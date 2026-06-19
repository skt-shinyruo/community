package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserCredentialActionApi;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import com.nowcoder.community.user.application.UserCredentialApplicationService;
import com.nowcoder.community.user.application.result.UserAuthenticationResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserCredentialApiAdapter implements UserCredentialQueryApi, UserCredentialActionApi {

    private final UserCredentialApplicationService applicationService;

    public UserCredentialApiAdapter(UserCredentialApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public UserAuthenticationResultView authenticate(String username, String password) {
        return toAuthenticationView(applicationService.authenticate(username, password));
    }

    @Override
    public UserCredentialView getByUserId(UUID userId) {
        return toCredentialView(applicationService.getByUserId(userId));
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
    public void updatePassword(UUID userId, String newPassword) {
        applicationService.updatePassword(userId, newPassword);
    }

    @Override
    public void resetPasswordAndRevokeRefreshSessions(UUID userId, String newPassword) {
        applicationService.resetPasswordAndRevokeRefreshSessions(userId, newPassword);
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
                result.status(),
                result.type(),
                result.headerUrl(),
                result.securityVersion()
        );
    }

    private UserCredentialResult toCredentialResult(UserCredentialView user) {
        if (user == null) {
            return null;
        }
        return new UserCredentialResult(
                user.userId(),
                user.username(),
                user.status(),
                user.type(),
                user.headerUrl(),
                user.securityVersion()
        );
    }
}
