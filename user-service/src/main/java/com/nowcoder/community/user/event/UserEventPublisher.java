package com.nowcoder.community.user.event;

import com.nowcoder.community.user.api.event.payload.ModerationStatusPayload;

public interface UserEventPublisher {

    void publishModerationStatusChanged(ModerationStatusPayload payload);
}
