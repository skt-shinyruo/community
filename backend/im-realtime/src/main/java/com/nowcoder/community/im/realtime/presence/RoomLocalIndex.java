package com.nowcoder.community.im.realtime.presence;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomLocalIndex {

    private final ConcurrentHashMap<Long, Set<String>> connectionIdsByRoomId = new ConcurrentHashMap<>();

    public void add(long roomId, String connectionId) {
        if (roomId <= 0 || connectionId == null || connectionId.isBlank()) {
            return;
        }
        connectionIdsByRoomId.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(connectionId);
    }

    public void remove(long roomId, String connectionId) {
        if (roomId <= 0 || connectionId == null || connectionId.isBlank()) {
            return;
        }
        Set<String> ids = connectionIdsByRoomId.get(roomId);
        if (ids == null) {
            return;
        }
        ids.remove(connectionId);
        if (ids.isEmpty()) {
            connectionIdsByRoomId.remove(roomId, ids);
        }
    }

    public Set<String> listConnectionIds(long roomId) {
        Set<String> ids = connectionIdsByRoomId.get(roomId);
        return ids == null ? Set.of() : Set.copyOf(ids);
    }
}

