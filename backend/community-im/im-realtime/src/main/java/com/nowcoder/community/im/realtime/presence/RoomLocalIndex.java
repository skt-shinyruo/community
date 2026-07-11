package com.nowcoder.community.im.realtime.presence;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoomLocalIndex {

    private final ConcurrentHashMap<UUID, Set<String>> connectionIdsByRoomId = new ConcurrentHashMap<>();

    private static final int DEFAULT_ROOM_SIZE_SAMPLE_RATE = 256;

    private final AtomicInteger indexedRooms = new AtomicInteger(0);
    private final int roomSizeSampleRate;
    private final DistributionSummary connectionsPerRoom;

    public RoomLocalIndex() {
        this(null, DEFAULT_ROOM_SIZE_SAMPLE_RATE);
    }

    public RoomLocalIndex(MeterRegistry meterRegistry, int roomSizeSampleRate) {
        this.roomSizeSampleRate = Math.max(1, roomSizeSampleRate);
        if (meterRegistry != null) {
            meterRegistry.gauge("im_ws_rooms_indexed", indexedRooms);
            this.connectionsPerRoom = DistributionSummary.builder("im_ws_connections_per_room")
                    .description("Sampled distribution of active connections per room (in-process, im-realtime)")
                    .baseUnit("connections")
                    .register(meterRegistry);
        } else {
            this.connectionsPerRoom = null;
        }
    }

    @Autowired
    public RoomLocalIndex(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable(), DEFAULT_ROOM_SIZE_SAMPLE_RATE);
    }

    public boolean add(UUID roomId, String connectionId) {
        if (roomId == null || connectionId == null || connectionId.isBlank()) {
            return false;
        }
        AtomicBoolean firstLocalConnection = new AtomicBoolean(false);
        connectionIdsByRoomId.compute(roomId, (ignored, existing) -> {
            Set<String> ids = existing;
            if (ids == null) {
                ids = ConcurrentHashMap.newKeySet();
                firstLocalConnection.set(true);
                indexedRooms.incrementAndGet();
            }
            ids.add(connectionId);
            recordRoomSizeSampled(ids);
            return ids;
        });
        return firstLocalConnection.get();
    }

    public boolean remove(UUID roomId, String connectionId) {
        if (roomId == null || connectionId == null || connectionId.isBlank()) {
            return false;
        }
        AtomicBoolean lastLocalConnectionRemoved = new AtomicBoolean(false);
        connectionIdsByRoomId.computeIfPresent(roomId, (ignored, ids) -> {
            ids.remove(connectionId);
            recordRoomSizeSampled(ids);
            if (ids.isEmpty()) {
                lastLocalConnectionRemoved.set(true);
                indexedRooms.decrementAndGet();
                return null;
            }
            return ids;
        });
        return lastLocalConnectionRemoved.get();
    }

    public boolean hasConnections(UUID roomId) {
        if (roomId == null) {
            return false;
        }
        Set<String> ids = connectionIdsByRoomId.get(roomId);
        return ids != null && !ids.isEmpty();
    }

    public void forEachConnectionId(UUID roomId, Consumer<String> consumer) {
        if (roomId == null || consumer == null) {
            return;
        }
        Set<String> ids = connectionIdsByRoomId.get(roomId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // Intentionally iterate over the concurrent set directly (no copying) for large-room performance.
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                consumer.accept(id);
            }
        }
    }

    private void recordRoomSizeSampled(Set<String> ids) {
        if (connectionsPerRoom == null || ids == null) {
            return;
        }
        if (roomSizeSampleRate > 1 && ThreadLocalRandom.current().nextInt(roomSizeSampleRate) != 0) {
            return;
        }
        try {
            int size = ids.size();
            if (size > 0) {
                connectionsPerRoom.record(size);
            }
        } catch (RuntimeException ignore) {
        }
    }
}
