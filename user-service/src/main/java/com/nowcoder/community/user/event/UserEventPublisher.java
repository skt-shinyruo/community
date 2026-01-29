package com.nowcoder.community.user.event;

import com.nowcoder.community.common.event.payload.ModerationStatusPayload;

public interface UserEventPublisher {

    void publishModerationStatusChanged(ModerationStatusPayload payload);
}

