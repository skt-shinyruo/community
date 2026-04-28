package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "social.events.publisher", havingValue = "memory")
public class InMemorySocialDomainEventPublisher implements SocialDomainEventPublisher {

    private final List<Object> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publishLikeChanged(LikeChangedDomainEvent event) {
        events.add(event);
    }

    @Override
    public void publishFollowCreated(FollowCreatedDomainEvent event) {
        events.add(event);
    }

    @Override
    public void publishBlockRelationChanged(BlockRelationChangedDomainEvent event) {
        events.add(event);
    }

    public List<Object> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}
