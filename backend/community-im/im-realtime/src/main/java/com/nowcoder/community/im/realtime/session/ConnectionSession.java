package com.nowcoder.community.im.realtime.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transport-free realtime connection: identity, bound session metadata and mutable
 * room/coalescing state. The transport adapter creates one instance per socket with a
 * production {@link ConnectionOutput}; every other module depends on the
 * {@link ConnectionState} / {@link ConnectionIdentity} / {@link ConnectionOutput}
 * interfaces and never sees the WebSocket session or Reactor sink.
 */
public class ConnectionSession implements ConnectionState {

    private final String connectionId;
    private final ConnectionOutput output;

    private volatile UUID userId;
    private volatile String sessionId = "";
    private volatile String workerId = "";
    private volatile String traceId = "";

    private final Set<UUID> joinedRooms = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> pendingRoomSeq = new ConcurrentHashMap<>();
    private final AtomicBoolean enqueuedForRoomFlush = new AtomicBoolean(false);

    public ConnectionSession(String connectionId, ConnectionOutput output) {
        this.connectionId = connectionId;
        this.output = output;
    }

    @Override
    public String connectionId() {
        return connectionId;
    }

    @Override
    public UUID userId() {
        return userId;
    }

    @Override
    public ConnectionOutput output() {
        return output;
    }

    public String traceId() {
        return traceId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String workerId() {
        return workerId;
    }

    public void bindTrace(String traceId) {
        this.traceId = traceId == null ? "" : traceId;
    }

    public void bindSession(String sessionId, UUID userId, String workerId) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.userId = userId;
        this.workerId = workerId == null ? "" : workerId;
    }

    public void bindUser(UUID userId) {
        this.userId = userId;
    }

    @Override
    public Set<UUID> joinedRoomsView() {
        return Collections.unmodifiableSet(joinedRooms);
    }

    @Override
    public void joinRoom(UUID roomId) {
        if (roomId == null) {
            return;
        }
        joinedRooms.add(roomId);
    }

    @Override
    public void leaveRoom(UUID roomId) {
        if (roomId == null) {
            return;
        }
        joinedRooms.remove(roomId);
        pendingRoomSeq.remove(roomId);
    }

    @Override
    public boolean enqueueForRoomFlushOnce() {
        return enqueuedForRoomFlush.compareAndSet(false, true);
    }

    @Override
    public void resetRoomFlushEnqueuedFlag() {
        enqueuedForRoomFlush.set(false);
    }

    @Override
    public void markRoomSeq(UUID roomId, long seq) {
        if (roomId == null || seq <= 0) {
            return;
        }
        pendingRoomSeq.merge(roomId, seq, Math::max);
    }

    @Override
    public Map<UUID, Long> drainPendingRoomSeq() {
        if (pendingRoomSeq.isEmpty()) {
            return Map.of();
        }
        HashMap<UUID, Long> drained = new HashMap<>();
        for (UUID roomId : pendingRoomSeq.keySet()) {
            Long v = pendingRoomSeq.remove(roomId);
            if (v != null) {
                drained.put(roomId, v);
            }
        }
        return drained.isEmpty() ? Map.of() : drained;
    }
}
