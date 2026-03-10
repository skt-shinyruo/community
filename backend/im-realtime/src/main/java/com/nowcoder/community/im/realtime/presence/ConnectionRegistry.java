package com.nowcoder.community.im.realtime.presence;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionRegistry {

    private final ConcurrentHashMap<String, WsConnection> byConnectionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Set<String>> connectionIdsByUserId = new ConcurrentHashMap<>();

    public void register(WsConnection conn) {
        if (conn == null) {
            return;
        }
        Integer userId = conn.userId();
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("connection userId not bound");
        }
        byConnectionId.put(conn.connectionId(), conn);
        connectionIdsByUserId.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(conn.connectionId());
    }

    public WsConnection get(String connectionId) {
        if (connectionId == null) {
            return null;
        }
        return byConnectionId.get(connectionId);
    }

    public void unregister(WsConnection conn) {
        if (conn == null) {
            return;
        }
        byConnectionId.remove(conn.connectionId());
        Integer userId = conn.userId();
        if (userId != null && userId > 0) {
            Set<String> ids = connectionIdsByUserId.get(userId);
            if (ids != null) {
                ids.remove(conn.connectionId());
                if (ids.isEmpty()) {
                    connectionIdsByUserId.remove(userId, ids);
                }
            }
        }
    }

    public Collection<WsConnection> listByUserId(int userId) {
        if (userId <= 0) {
            return List.of();
        }
        Set<String> ids = connectionIdsByUserId.get(userId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        ArrayList<WsConnection> list = new ArrayList<>(ids.size());
        for (String id : ids) {
            WsConnection conn = byConnectionId.get(id);
            if (conn != null) {
                list.add(conn);
            }
        }
        return list;
    }

    public int onlineConnectionCount() {
        return byConnectionId.size();
    }

    public Map<String, WsConnection> snapshotAll() {
        return Map.copyOf(byConnectionId);
    }
}
