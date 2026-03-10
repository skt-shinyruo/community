package com.nowcoder.community.im.realtime.push;

import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Room-level coalescing to avoid O(roomOnlineMembers * roomMsgRate) work.
 *
 * <p>We only keep the latest seq per room between flush ticks, then fanout once per tick.
 * Connection-level batching is handled by {@link RoomUpdateCoalescer}.</p>
 */
@Component
public class RoomFanoutCoalescer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RoomFanoutCoalescer.class);

    private final ConnectionRegistry connectionRegistry;
    private final RoomLocalIndex roomLocalIndex;
    private final RoomUpdateCoalescer roomUpdateCoalescer;
    private final Duration flushInterval;

    private final ConcurrentHashMap<Long, Long> latestSeqByRoomId = new ConcurrentHashMap<>();
    private final Set<Long> enqueuedRooms = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Long> pendingRoomIds = new ConcurrentLinkedQueue<>();
    private final Disposable ticker;

    public RoomFanoutCoalescer(
            ConnectionRegistry connectionRegistry,
            RoomLocalIndex roomLocalIndex,
            RoomUpdateCoalescer roomUpdateCoalescer,
            @Value("${im.ws.room-flush-interval-ms:50}") long flushIntervalMs
    ) {
        this.connectionRegistry = connectionRegistry;
        this.roomLocalIndex = roomLocalIndex;
        this.roomUpdateCoalescer = roomUpdateCoalescer;
        long ms = Math.min(Math.max(10L, flushIntervalMs), 1000L);
        this.flushInterval = Duration.ofMillis(ms);
        this.ticker = Flux.interval(this.flushInterval)
                .onBackpressureDrop()
                .subscribe(tick -> flushOnceSafely());
    }

    public void markRoomUpdated(long roomId, long lastSeq) {
        if (roomId <= 0 || lastSeq <= 0) {
            return;
        }
        latestSeqByRoomId.merge(roomId, lastSeq, Math::max);
        if (enqueuedRooms.add(roomId)) {
            pendingRoomIds.add(roomId);
        }
    }

    private void flushOnceSafely() {
        try {
            flushOnce();
        } catch (RuntimeException e) {
            log.warn("[room-fanout] flush failed: {}", e.toString());
        }
    }

    private void flushOnce() {
        while (true) {
            Long roomId = pendingRoomIds.poll();
            if (roomId == null) {
                return;
            }
            // Allow re-enqueue if new updates arrive while we are fanning out.
            enqueuedRooms.remove(roomId);

            Long lastSeq = latestSeqByRoomId.remove(roomId);
            if (lastSeq == null || lastSeq <= 0) {
                continue;
            }

            roomLocalIndex.forEachConnectionId(roomId, connectionId -> {
                WsConnection conn = connectionRegistry.get(connectionId);
                if (conn != null) {
                    roomUpdateCoalescer.markRoomUpdated(conn, roomId, lastSeq);
                }
            });
        }
    }

    @Override
    public void destroy() {
        if (ticker != null) {
            ticker.dispose();
        }
    }
}
