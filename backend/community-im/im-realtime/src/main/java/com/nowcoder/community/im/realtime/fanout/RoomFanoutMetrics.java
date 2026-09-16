package com.nowcoder.community.im.realtime.fanout;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RoomFanoutMetrics {

    private final Counter ownerEventsConsumed;
    private final Counter routesPlanned;
    private final Counter commandsSent;
    private final Counter emptyTargets;
    private final Counter routeFailures;
    private final Counter targetAccepted;
    private final Counter targetDuplicates;
    private final Counter targetRejected;

    RoomFanoutMetrics(MeterRegistry meterRegistry) {
        this.ownerEventsConsumed = Counter.builder("im_room_fanout_events_consumed")
                .tag("path", "owner")
                .register(meterRegistry);
        this.routesPlanned = Counter.builder("im_room_fanout_routes_planned")
                .register(meterRegistry);
        this.commandsSent = Counter.builder("im_room_fanout_commands_sent")
                .register(meterRegistry);
        this.emptyTargets = Counter.builder("im_room_fanout_empty_targets")
                .register(meterRegistry);
        this.routeFailures = Counter.builder("im_room_fanout_route_failures")
                .register(meterRegistry);
        this.targetAccepted = targetResultCounter(meterRegistry, "accepted");
        this.targetDuplicates = targetResultCounter(meterRegistry, "duplicate");
        this.targetRejected = targetResultCounter(meterRegistry, "rejected");
    }

    void ownerEventConsumed() {
        ownerEventsConsumed.increment();
    }

    void routesPlanned(int count) {
        if (count > 0) {
            routesPlanned.increment(count);
        }
    }

    void commandSent() {
        commandsSent.increment();
    }

    void emptyTargetSet() {
        emptyTargets.increment();
    }

    void routeFailed() {
        routeFailures.increment();
    }

    void targetAccepted() {
        targetAccepted.increment();
    }

    void targetDuplicate() {
        targetDuplicates.increment();
    }

    void targetRejected() {
        targetRejected.increment();
    }

    private static Counter targetResultCounter(MeterRegistry meterRegistry, String result) {
        return Counter.builder("im_room_fanout_target_results")
                .tag("result", result)
                .register(meterRegistry);
    }
}
