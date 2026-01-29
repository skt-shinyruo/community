package com.nowcoder.community.user.event;

import com.nowcoder.community.common.event.payload.ModerationStatusPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "user.events.publisher", havingValue = "memory")
public class InMemoryUserEventPublisher implements UserEventPublisher {

    private final List<Object> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publishModerationStatusChanged(ModerationStatusPayload payload) {
        events.add(payload);
    }

    public List<Object> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}

