package com.nowcoder.community.im.realtime.push;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class RoomUpdateCoalescer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RoomUpdateCoalescer.class);

    private final ConnectionRegistry connectionRegistry;
    private final JsonCodec jsonCodec;
    private final Duration flushInterval;

    private final ConcurrentLinkedQueue<String> pendingConnectionIds = new ConcurrentLinkedQueue<>();
    private final Disposable ticker;

    public RoomUpdateCoalescer(
            ConnectionRegistry connectionRegistry,
            JsonCodec jsonCodec,
            @Value("${im.ws.room-flush-interval-ms:50}") long flushIntervalMs
    ) {
        this.connectionRegistry = connectionRegistry;
        this.jsonCodec = jsonCodec;
        long ms = Math.min(Math.max(10L, flushIntervalMs), 1000L);
        this.flushInterval = Duration.ofMillis(ms);
        this.ticker = Flux.interval(this.flushInterval)
                .onBackpressureDrop()
                .subscribe(tick -> flushOnceSafely());
    }

    public void markRoomUpdated(WsConnection conn, UUID roomId, long lastSeq) {
        if (conn == null) {
            return;
        }
        conn.markRoomSeq(roomId, lastSeq);
        if (conn.enqueueForRoomFlushOnce()) {
            pendingConnectionIds.add(conn.connectionId());
        }
    }

    private void flushOnceSafely() {
        try {
            flushOnce();
        } catch (RuntimeException e) {
            log.warn("[room-coalescer] flush failed: {}", e.toString());
        }
    }

    private void flushOnce() {
        int processed = 0;
        while (true) {
            String connectionId = pendingConnectionIds.poll();
            if (connectionId == null) {
                return;
            }
            processed++;

            WsConnection conn = connectionRegistry.get(connectionId);
            if (conn == null) {
                continue;
            }

            conn.resetRoomFlushEnqueuedFlag();
            Map<UUID, Long> updates = conn.drainPendingRoomSeq();
            if (updates.isEmpty()) {
                continue;
            }

            ArrayList<RoomUpdatedItem> items = new ArrayList<>(updates.size());
            for (var e : updates.entrySet()) {
                items.add(new RoomUpdatedItem(e.getKey(), e.getValue()));
            }
            items.sort(Comparator.comparing(RoomUpdatedItem::roomId));

            String json;
            try {
                json = jsonCodec.toJson(new RoomUpdatedBatch(items));
            } catch (JsonCodecException e) {
                continue;
            }
            conn.trySendText(json);
        }
    }

    @Override
    public void destroy() {
        if (ticker != null) {
            ticker.dispose();
        }
    }

    public record RoomUpdatedBatch(String type, ArrayList<RoomUpdatedItem> items) {
        public RoomUpdatedBatch(ArrayList<RoomUpdatedItem> items) {
            this("roomUpdatedBatch", items);
        }
    }

    public record RoomUpdatedItem(UUID roomId, long lastSeq) {
    }
}
