package com.nowcoder.community.im.realtime.presence;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Component
public class ConnectionRegistry {

    private final ConcurrentHashMap<String, WsConnection> byConnectionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Set<String>> connectionIdsByUserId = new ConcurrentHashMap<>();

    private final AtomicInteger onlineConnections = new AtomicInteger(0);
    private final AtomicInteger onlineUsers = new AtomicInteger(0);

    private final DistributionSummary connectionsPerUser;

    public ConnectionRegistry() {
        this((MeterRegistry) null);
    }

    public ConnectionRegistry(MeterRegistry meterRegistry) {
        if (meterRegistry != null) {
            meterRegistry.gauge("im_ws_online_connections", onlineConnections);
            meterRegistry.gauge("im_ws_online_users", onlineUsers);
            this.connectionsPerUser = DistributionSummary.builder("im_ws_connections_per_user")
                    .description("Distribution of active websocket connections per online user (in-process, im-realtime)")
                    .baseUnit("connections")
                    .register(meterRegistry);
        } else {
            this.connectionsPerUser = null;
        }
    }

    @Autowired
    public ConnectionRegistry(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    public void register(WsConnection conn) {
        if (conn == null) {
            return;
        }
        Integer userId = conn.userId();
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("connection userId not bound");
        }

        WsConnection prev = byConnectionId.put(conn.connectionId(), conn);
        if (prev == null) {
            onlineConnections.incrementAndGet();
        }

        Set<String> ids = connectionIdsByUserId.get(userId);
        if (ids == null) {
            Set<String> created = ConcurrentHashMap.newKeySet();
            ids = connectionIdsByUserId.putIfAbsent(userId, created);
            if (ids == null) {
                ids = created;
                onlineUsers.incrementAndGet();
            }
        }
        ids.add(conn.connectionId());
        recordConnectionsPerUserSize(ids);
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
        WsConnection removed = byConnectionId.remove(conn.connectionId());
        if (removed != null) {
            onlineConnections.decrementAndGet();
        }
        Integer userId = conn.userId();
        if (userId != null && userId > 0) {
            Set<String> ids = connectionIdsByUserId.get(userId);
            if (ids != null) {
                ids.remove(conn.connectionId());
                if (ids.isEmpty()) {
                    if (connectionIdsByUserId.remove(userId, ids)) {
                        onlineUsers.decrementAndGet();
                    }
                }
                recordConnectionsPerUserSize(ids);
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

    public void forEachConnectionByUserId(int userId, Consumer<WsConnection> consumer) {
        if (userId <= 0 || consumer == null) {
            return;
        }
        Set<String> ids = connectionIdsByUserId.get(userId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            WsConnection conn = byConnectionId.get(id);
            if (conn != null) {
                consumer.accept(conn);
            }
        }
    }

    public int onlineConnectionCount() {
        return onlineConnections.get();
    }

    public Map<String, WsConnection> snapshotAll() {
        return Map.copyOf(byConnectionId);
    }

    private void recordConnectionsPerUserSize(Set<String> ids) {
        if (connectionsPerUser == null || ids == null) {
            return;
        }
        try {
            int size = ids.size();
            if (size > 0) {
                connectionsPerUser.record(size);
            }
        } catch (RuntimeException ignore) {
        }
    }
}
