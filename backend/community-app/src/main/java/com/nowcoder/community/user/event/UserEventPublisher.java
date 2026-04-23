package com.nowcoder.community.user.event;

import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;

public interface UserEventPublisher {

    void publishUserPolicyChanged(UserPolicyChangedPayload payload);
}
