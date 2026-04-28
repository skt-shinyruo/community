package com.nowcoder.community.user.application.port;

import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;

public interface UserEventPublisher {

    void publishUserPolicyChanged(UserPolicyChangedPayload payload);
}
