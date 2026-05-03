package com.nowcoder.community.user.api.action;

import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;

import java.time.Duration;
import java.util.UUID;

public interface UserRegistrationActionApi {

    PendingRegistrationUserView registerPendingUser(String username, String password, String email, Duration pendingTtl);

    PreparedRegistrationUserView prepareRegistrationUser(String username, String password, String email);

    UserCredentialView createVerifiedRegistrationUser(VerifiedRegistrationUserCommand command);

    UserCredentialView activatePendingUser(UUID userId);

    void deletePendingUser(UUID userId);

    int cleanupExpiredPendingUsers(Duration pendingTtl);
}
