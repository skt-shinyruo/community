package com.nowcoder.community.content.event;

import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.event.payload.ModerationCommandPayload;
import com.nowcoder.community.common.event.payload.ModerationPayload;
import com.nowcoder.community.common.event.payload.PostPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "content.events.publisher", havingValue = "memory")
public class InMemoryContentEventPublisher implements ContentEventPublisher {

    private final List<Object> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publishPostPublished(PostPayload payload) {
        events.add(payload);
    }

    @Override
    public void publishPostUpdated(PostPayload payload) {
        events.add(payload);
    }

    @Override
    public void publishPostDeleted(PostPayload payload) {
        events.add(payload);
    }

    @Override
    public void publishCommentCreated(CommentPayload payload) {
        events.add(payload);
    }

    @Override
    public void publishModerationActionApplied(ModerationPayload payload) {
        events.add(payload);
    }

    @Override
    public void publishModerationCommandRequested(ModerationCommandPayload payload) {
        events.add(payload);
    }

    public List<Object> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}
