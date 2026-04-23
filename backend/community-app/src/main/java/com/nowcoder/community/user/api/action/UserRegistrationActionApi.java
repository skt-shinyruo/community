package com.nowcoder.community.user.api.action;

import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
import com.nowcoder.community.user.api.model.UserCredentialView;

import java.time.Duration;
import java.util.UUID;

public interface UserRegistrationActionApi {

    PendingRegistrationUserView registerPendingUser(String username, String password, String email, Duration pendingTtl);

    UserCredentialView activatePendingUser(UUID userId);

    void deletePendingUser(UUID userId);

    int cleanupExpiredPendingUsers(Duration pendingTtl);
}
