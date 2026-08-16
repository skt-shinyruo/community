package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class RoomFanoutOwnerService {

    private final RoomPresenceDirectory roomPresenceDirectory;
    private final RoomFanoutDispatcher dispatcher;
    private final RoomFanoutMetrics metrics;

    public RoomFanoutOwnerService(
            RoomPresenceDirectory roomPresenceDirectory,
            RoomFanoutDispatcher dispatcher,
            RoomFanoutMetrics metrics
    ) {
        this.roomPresenceDirectory = roomPresenceDirectory;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
    }

    public void routeAndDispatch(RoomMessagePersistedEvent event) {
        if (event == null || event.roomId() == null || event.seq() <= 0) {
            return;
        }
        List<String> workerIds;
        try {
            workerIds = roomPresenceDirectory.activeWorkerIds(event.roomId()).stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (RuntimeException failure) {
            metrics.routeFailed();
            throw failure;
        }
        if (workerIds.isEmpty()) {
            metrics.emptyTargetSet();
            return;
        }
        metrics.routesPlanned(workerIds.size());
        RuntimeException firstFailure = null;
        for (String workerId : workerIds) {
            try {
                dispatcher.dispatch(new RoomFanoutCommand(
                        workerId,
                        event.roomId(),
                        event.seq(),
                        event.eventId(),
                        event.createdAtEpochMs()
                ));
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

}
