package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomFanoutOwnerService {

    private final RoomFanoutRoutingService routingService;
    private final RoomFanoutDispatcher dispatcher;
    private final RoomFanoutMetrics metrics;

    public RoomFanoutOwnerService(
            RoomFanoutRoutingService routingService,
            RoomFanoutDispatcher dispatcher,
            RoomFanoutMetrics metrics
    ) {
        this.routingService = routingService;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
    }

    public void routeAndDispatch(RoomMessagePersistedEvent event) {
        if (event == null || event.roomId() == null || event.seq() <= 0) {
            return;
        }
        List<RoomFanoutRoute> routes;
        try {
            routes = routingService.routesFor(event.roomId(), event.seq());
        } catch (RuntimeException failure) {
            metrics.routeFailed();
            throw failure;
        }
        if (routes.isEmpty()) {
            metrics.emptyTargetSet();
            return;
        }
        metrics.routesPlanned(routes.size());
        RuntimeException firstFailure = null;
        for (RoomFanoutRoute route : routes) {
            try {
                dispatcher.dispatch(commandFor(event, route));
                metrics.commandSent();
            } catch (RuntimeException failure) {
                metrics.routeFailed();
                if (firstFailure == null) {
                    firstFailure = failure;
                }
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException(
                    "room fanout routed dispatch failed: roomId=" + event.roomId() + ", seq=" + event.seq(),
                    firstFailure
            );
        }
    }

    private static RoomFanoutCommand commandFor(RoomMessagePersistedEvent event, RoomFanoutRoute route) {
        return new RoomFanoutCommand(
                route.targetWorkerId(),
                route.roomId(),
                route.lastSeq(),
                event.eventId(),
                event.createdAtEpochMs()
        );
    }
}
