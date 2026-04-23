package com.nowcoder.community.user.event;

import com.nowcoder.community.user.contracts.event.UserModerationChangedPayload;

public interface UserEventPublisher {

    void publishUserModerationChanged(UserModerationChangedPayload payload);
}
