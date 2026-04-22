package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.PendingRegistrationUserView;

import java.time.Duration;
import java.util.UUID;

public interface UserPendingRegistrationQueryApi {

    PendingRegistrationUserView getPendingUser(UUID userId, Duration pendingTtl);
}
