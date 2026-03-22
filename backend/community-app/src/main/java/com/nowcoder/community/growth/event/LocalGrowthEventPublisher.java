package com.nowcoder.community.growth.event;

import com.nowcoder.community.growth.event.payload.CheckInPayload;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LocalGrowthEventPublisher implements GrowthEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalGrowthEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishCheckInCompleted(String eventId, CheckInPayload payload) {
        String resolvedEventId = eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId.trim();
        applicationEventPublisher.publishEvent(new GrowthLocalEvent(resolvedEventId, GrowthEventTypes.CHECK_IN_COMPLETED, payload));
    }
}
