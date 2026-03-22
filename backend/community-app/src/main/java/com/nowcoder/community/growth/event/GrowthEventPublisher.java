package com.nowcoder.community.growth.event;

import com.nowcoder.community.growth.event.payload.CheckInPayload;

public interface GrowthEventPublisher {

    void publishCheckInCompleted(String eventId, CheckInPayload payload);
}
