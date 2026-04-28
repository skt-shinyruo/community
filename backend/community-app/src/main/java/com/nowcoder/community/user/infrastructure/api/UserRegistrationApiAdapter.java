package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserPendingRegistrationQueryApi;
import com.nowcoder.community.user.application.UserRegistrationApplicationService;
import com.nowcoder.community.user.application.result.PendingRegistrationUserResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class UserRegistrationApiAdapter implements UserRegistrationActionApi, UserPendingRegistrationQueryApi {

    private final UserRegistrationApplicationService applicationService;

    public UserRegistrationApiAdapter(UserRegistrationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public PendingRegistrationUserView registerPendingUser(String username, String password, String email, Duration pendingTtl) {
        return toPendingView(applicationService.registerPendingUser(username, password, email, pendingTtl));
    }

    @Override
    public UserCredentialView activatePendingUser(UUID userId) {
        return toCredentialView(applicationService.activatePendingUser(userId));
    }

    @Override
    public void deletePendingUser(UUID userId) {
        applicationService.deletePendingUser(userId);
    }

    @Override
    public int cleanupExpiredPendingUsers(Duration pendingTtl) {
        return applicationService.cleanupExpiredPendingUsers(pendingTtl);
    }

    @Override
    public PendingRegistrationUserView getPendingUser(UUID userId, Duration pendingTtl) {
        return toPendingView(applicationService.getPendingUser(userId, pendingTtl));
    }

    private PendingRegistrationUserView toPendingView(PendingRegistrationUserResult result) {
        if (result == null) {
            return null;
        }
        return new PendingRegistrationUserView(
                result.userId(),
                result.username(),
                result.email(),
                result.status(),
                result.type(),
                result.headerUrl()
        );
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
                result.headerUrl()
        );
    }
}
