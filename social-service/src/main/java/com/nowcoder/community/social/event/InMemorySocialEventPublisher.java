package com.nowcoder.community.social.event;

import com.nowcoder.community.common.event.payload.FollowPayload;
import com.nowcoder.community.common.event.payload.LikePayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "social.events.publisher", havingValue = "memory")
public class InMemorySocialEventPublisher implements SocialEventPublisher {

    private final List<Object> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publishLikeCreated(LikePayload payload) {
        events.add(payload);
    }

    @Override
    public void publishFollowCreated(FollowPayload payload) {
        events.add(payload);
    }

    public List<Object> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}

