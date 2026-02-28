package com.nowcoder.community.social.event;

import com.nowcoder.community.social.api.event.payload.BlockPayload;
import com.nowcoder.community.social.api.event.payload.FollowPayload;
import com.nowcoder.community.social.api.event.payload.LikePayload;
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
    public void publishLikeRemoved(LikePayload payload) {
        events.add(payload);
    }

    @Override
    public void publishFollowCreated(FollowPayload payload) {
        events.add(payload);
    }

    @Override
    public void publishBlockRelationChanged(BlockPayload payload) {
        events.add(payload);
    }

    public List<Object> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}
