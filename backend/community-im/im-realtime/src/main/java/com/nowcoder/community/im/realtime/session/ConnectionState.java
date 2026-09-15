package com.nowcoder.community.im.realtime.session;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable per-connection state shared with non-transport modules: joined-room
 * membership for presence orchestration and the pending room-update buffer for
 * coalesced fanout. Implementations are transport-free; the WebSocket session and
 * Reactor sink stay behind {@link #output()}.
 */
public interface ConnectionState extends ConnectionIdentity {

    ConnectionOutput output();

    Set<UUID> joinedRoomsView();

    void joinRoom(UUID roomId);

    void leaveRoom(UUID roomId);

    /** Marks at most one pending flush enqueue; false when already enqueued. */
    boolean enqueueForRoomFlushOnce();

    void resetRoomFlushEnqueuedFlag();

    void markRoomSeq(UUID roomId, long seq);

    Map<UUID, Long> drainPendingRoomSeq();
}
