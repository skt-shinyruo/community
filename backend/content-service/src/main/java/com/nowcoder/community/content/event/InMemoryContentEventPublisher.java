package com.nowcoder.community.content.event;

import com.nowcoder.community.platform.tx.AfterCommitExecutor;
import com.nowcoder.community.content.api.event.payload.CommentPayload;
import com.nowcoder.community.content.api.event.payload.ModerationCommandPayload;
import com.nowcoder.community.content.api.event.payload.ModerationPayload;
import com.nowcoder.community.content.api.event.payload.PostPayload;
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
        AfterCommitExecutor.runAfterCommit(() -> events.add(payload));
    }

    @Override
    public void publishPostUpdated(PostPayload payload) {
        AfterCommitExecutor.runAfterCommit(() -> events.add(payload));
    }

    @Override
    public void publishPostDeleted(PostPayload payload) {
        AfterCommitExecutor.runAfterCommit(() -> events.add(payload));
    }

    @Override
    public void publishCommentCreated(CommentPayload payload) {
        AfterCommitExecutor.runAfterCommit(() -> events.add(payload));
    }

    @Override
    public void publishCommentDeleted(CommentPayload payload) {
        AfterCommitExecutor.runAfterCommit(() -> events.add(payload));
    }

    @Override
    public void publishModerationActionApplied(ModerationPayload payload) {
        AfterCommitExecutor.runAfterCommit(() -> events.add(payload));
    }

    @Override
    public void publishModerationCommandRequested(ModerationCommandPayload payload) {
        AfterCommitExecutor.runAfterCommit(() -> events.add(payload));
    }

    public List<Object> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}
