package com.nowcoder.community.im.realtime.session;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link ConnectionOutput} for transport-free tests: accepted frames are
 * queued for polling, complete/close are recorded. Presence, push and coalescing
 * tests exercise the same output interface the production WebSocket adapter implements,
 * without touching a Reactor sink.
 */
public final class InMemoryConnectionOutput implements ConnectionOutput {

    private final LinkedBlockingQueue<String> sent = new LinkedBlockingQueue<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicInteger closeCount = new AtomicInteger(0);
    private volatile Duration lastCloseTimeout;

    @Override
    public boolean trySendText(String text) {
        if (text == null) {
            return true;
        }
        if (completed.get()) {
            return false;
        }
        sent.offer(text);
        return true;
    }

    @Override
    public void complete() {
        completed.set(true);
    }

    @Override
    public void closeAsync(Duration timeout) {
        closeCount.incrementAndGet();
        lastCloseTimeout = timeout;
    }

    @Override
    public int outboundBacklog() {
        return sent.size();
    }

    /** Awaits the next accepted frame, or returns null when none arrives in time. */
    public String poll(Duration timeout) throws InterruptedException {
        return sent.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public List<String> sentFrames() {
        return List.copyOf(sent);
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public int closeCount() {
        return closeCount.get();
    }

    public Duration lastCloseTimeout() {
        return lastCloseTimeout;
    }
}
