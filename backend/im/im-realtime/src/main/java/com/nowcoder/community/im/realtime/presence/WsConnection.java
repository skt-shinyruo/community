package com.nowcoder.community.im.realtime.presence;

import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WsConnection {

    private final String connectionId;
    private final WebSocketSession session;
    private final Sinks.Many<String> outbound;
    private final AtomicInteger outboundBacklog;
    private final int maxOutboundBacklog;

    private volatile Integer userId;

    private final Set<Long> joinedRooms = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, Long> pendingRoomSeq = new ConcurrentHashMap<>();
    private final AtomicBoolean enqueuedForRoomFlush = new AtomicBoolean(false);

    public WsConnection(String connectionId, WebSocketSession session, int maxOutboundBacklog) {
        this.connectionId = connectionId;
        this.session = session;
        this.maxOutboundBacklog = Math.max(1, maxOutboundBacklog);
        this.outbound = Sinks.many().unicast().onBackpressureBuffer();
        this.outboundBacklog = new AtomicInteger(0);
    }

    public String connectionId() {
        return connectionId;
    }

    public WebSocketSession session() {
        return session;
    }

    public Integer userId() {
        return userId;
    }

    public void bindUser(int userId) {
        this.userId = userId;
    }

    public Set<Long> joinedRoomsView() {
        return Collections.unmodifiableSet(joinedRooms);
    }

    public void joinRoom(long roomId) {
        if (roomId <= 0) {
            return;
        }
        joinedRooms.add(roomId);
    }

    public void leaveRoom(long roomId) {
        if (roomId <= 0) {
            return;
        }
        joinedRooms.remove(roomId);
        pendingRoomSeq.remove(roomId);
    }

    public boolean enqueueForRoomFlushOnce() {
        return enqueuedForRoomFlush.compareAndSet(false, true);
    }

    public void resetRoomFlushEnqueuedFlag() {
        enqueuedForRoomFlush.set(false);
    }

    public void markRoomSeq(long roomId, long seq) {
        if (roomId <= 0 || seq <= 0) {
            return;
        }
        pendingRoomSeq.merge(roomId, seq, Math::max);
    }

    public Map<Long, Long> drainPendingRoomSeq() {
        if (pendingRoomSeq.isEmpty()) {
            return Map.of();
        }
        HashMap<Long, Long> drained = new HashMap<>();
        for (Long roomId : pendingRoomSeq.keySet()) {
            Long v = pendingRoomSeq.remove(roomId);
            if (v != null) {
                drained.put(roomId, v);
            }
        }
        return drained.isEmpty() ? Map.of() : drained;
    }

    public Sinks.Many<String> outboundSink() {
        return outbound;
    }

    public int outboundBacklog() {
        return outboundBacklog.get();
    }

    public boolean trySendText(String text) {
        if (text == null) {
            return true;
        }
        int afterInc = outboundBacklog.incrementAndGet();
        if (afterInc > maxOutboundBacklog) {
            outboundBacklog.decrementAndGet();
            closeAsync(Duration.ofSeconds(1));
            return false;
        }

        Sinks.EmitResult result = outbound.tryEmitNext(text);
        if (result.isFailure()) {
            outboundBacklog.decrementAndGet();
            return false;
        }
        return true;
    }

    public void onOutboundDelivered() {
        outboundBacklog.decrementAndGet();
    }

    public void complete() {
        outbound.tryEmitComplete();
    }

    public void closeAsync(Duration timeout) {
        try {
            session.close()
                    .timeout(timeout == null ? Duration.ofSeconds(1) : timeout)
                    .onErrorResume(e -> Mono.empty())
                    .subscribe();
        } catch (RuntimeException ignore) {
        }
    }
}
