package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.PostScoreQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@ConditionalOnProperty(name = "content.storage", havingValue = "memory")
public class InMemoryPostScoreQueue implements PostScoreQueue {

    private final Queue<UUID> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void add(UUID postId) {
        queue.add(postId);
    }

    @Override
    public UUID pop() {
        return queue.poll();
    }
}
