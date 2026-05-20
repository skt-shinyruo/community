package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class RoomFanoutOwnerCoalescer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RoomFanoutOwnerCoalescer.class);

    private final RoomFanoutRoutingService routingService;
    private final RoomFanoutDispatcher dispatcher;
    private final RoomFanoutProperties properties;
    private final RoomFanoutMetrics metrics;
    private final ConcurrentHashMap<UUID, PendingRoomEvent> latestByRoomId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap.KeySetView<UUID, Boolean> enqueuedRoomIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<UUID> pendingRoomIds = new ConcurrentLinkedQueue<>();
    private final Disposable ticker;

    public RoomFanoutOwnerCoalescer(
            RoomFanoutRoutingService routingService,
            RoomFanoutDispatcher dispatcher,
            RoomFanoutProperties properties,
            RoomFanoutMetrics metrics
    ) {
        this.routingService = routingService;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.metrics = metrics;
        this.ticker = Flux.interval(properties.normalizedOwnerFlushInterval())
                .onBackpressureDrop()
                .subscribe(tick -> flushOnceSafely());
    }

    public void markRoomUpdated(RoomMessagePersistedEvent event) {
        if (event == null || event.roomId() == null || event.seq() <= 0) {
            return;
        }
        latestByRoomId.merge(
                event.roomId(),
                PendingRoomEvent.from(event),
                (left, right) -> left.lastSeq() >= right.lastSeq() ? left : right
        );
        if (enqueuedRoomIds.add(event.roomId())) {
            pendingRoomIds.add(event.roomId());
        }
    }

    void routeAndDispatchNow(RoomMessagePersistedEvent event) {
        if (event == null || event.roomId() == null || event.seq() <= 0) {
            return;
        }
        PendingRoomEvent pending = PendingRoomEvent.from(event);
        List<RoomFanoutRoute> routes;
        try {
            routes = routingService.routesFor(event.roomId(), pending.lastSeq());
        } catch (RuntimeException ex) {
            metrics.routeFailed();
            log.warn(
                    "[room-fanout-owner] route planning failed: roomId={}, seq={}, error={}",
                    event.roomId(),
                    pending.lastSeq(),
                    ex.toString()
            );
            throw ex;
        }
        if (routes.isEmpty()) {
            metrics.emptyTargetSet();
            return;
        }
        metrics.routesPlanned(routes.size());
        RuntimeException firstFailure = null;
        for (RoomFanoutRoute route : routes) {
            try {
                dispatchOrThrow(route, pending);
            } catch (RuntimeException ex) {
                if (firstFailure == null) {
                    firstFailure = ex;
                }
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException(
                    "room fanout routed dispatch failed: roomId=" + event.roomId() + ", seq=" + pending.lastSeq(),
                    firstFailure
            );
        }
    }

    void flushOnce() {
        List<PendingRetry> retries = new ArrayList<>();
        while (true) {
            UUID roomId = pendingRoomIds.poll();
            if (roomId == null) {
                requeueRetries(retries);
                return;
            }
            enqueuedRoomIds.remove(roomId);
            PendingRoomEvent pending = latestByRoomId.remove(roomId);
            if (pending == null || pending.lastSeq() <= 0) {
                continue;
            }
            List<RoomFanoutRoute> routes;
            try {
                routes = routingService.routesFor(roomId, pending.lastSeq());
            } catch (RuntimeException ex) {
                metrics.routeFailed();
                log.warn(
                        "[room-fanout-owner] route planning failed: roomId={}, seq={}, error={}",
                        roomId,
                        pending.lastSeq(),
                        ex.toString()
                );
                retries.add(new PendingRetry(roomId, pending));
                continue;
            }
            if (routes.isEmpty()) {
                metrics.emptyTargetSet();
                continue;
            }
            metrics.routesPlanned(routes.size());
            if (properties.isShadowMode()) {
                continue;
            }
            boolean failed = false;
            for (RoomFanoutRoute route : routes) {
                failed = !dispatch(route, pending) || failed;
            }
            if (failed) {
                retries.add(new PendingRetry(roomId, pending));
            }
        }
    }

    private void flushOnceSafely() {
        try {
            flushOnce();
        } catch (RuntimeException ex) {
            log.warn("[room-fanout-owner] flush failed: {}", ex.toString());
        }
    }

    private boolean dispatch(RoomFanoutRoute route, PendingRoomEvent pending) {
        try {
            dispatchOrThrow(route, pending);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void dispatchOrThrow(RoomFanoutRoute route, PendingRoomEvent pending) {
        try {
            dispatcher.dispatch(new RoomFanoutCommand(
                    route.targetWorkerId(),
                    route.roomId(),
                    route.lastSeq(),
                    pending.sourceEventId(),
                    pending.createdAtEpochMs()
            ));
            metrics.commandSent();
        } catch (RuntimeException ex) {
            metrics.routeFailed();
            log.warn(
                    "[room-fanout-owner] target dispatch failed: roomId={}, workerId={}, seq={}, error={}",
                    route.roomId(),
                    route.targetWorkerId(),
                    route.lastSeq(),
                    ex.toString()
            );
            throw ex;
        }
    }

    private void requeueRetries(List<PendingRetry> retries) {
        if (retries.isEmpty()) {
            return;
        }
        for (PendingRetry retry : retries) {
            latestByRoomId.merge(
                    retry.roomId(),
                    retry.pending(),
                    (left, right) -> left.lastSeq() >= right.lastSeq() ? left : right
            );
            if (enqueuedRoomIds.add(retry.roomId())) {
                pendingRoomIds.add(retry.roomId());
            }
        }
    }

    @Override
    public void destroy() {
        if (ticker != null) {
            ticker.dispose();
        }
    }

    private record PendingRoomEvent(String sourceEventId, long lastSeq, long createdAtEpochMs) {
        private static PendingRoomEvent from(RoomMessagePersistedEvent event) {
            return new PendingRoomEvent(event.eventId(), event.seq(), event.createdAtEpochMs());
        }
    }

    private record PendingRetry(UUID roomId, PendingRoomEvent pending) {
    }
}
