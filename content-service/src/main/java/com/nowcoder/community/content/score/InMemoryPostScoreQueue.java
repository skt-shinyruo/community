package com.nowcoder.community.content.score;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@ConditionalOnProperty(name = "content.storage", havingValue = "memory")
public class InMemoryPostScoreQueue implements PostScoreQueue {

    private final Queue<Integer> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void add(int postId) {
        queue.add(postId);
    }

    @Override
    public Integer pop() {
        return queue.poll();
    }
}

