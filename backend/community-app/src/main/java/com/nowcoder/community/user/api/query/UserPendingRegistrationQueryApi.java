package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.PendingRegistrationUserView;

import java.time.Duration;

public interface UserPendingRegistrationQueryApi {

    PendingRegistrationUserView getPendingUser(int userId, Duration pendingTtl);
}
