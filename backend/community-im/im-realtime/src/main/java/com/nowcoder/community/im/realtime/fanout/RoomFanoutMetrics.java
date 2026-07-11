package com.nowcoder.community.im.realtime.fanout;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public RoomFanoutMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    RoomFanoutMetrics(MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            this.ownerEventsConsumed = null;
            this.routesPlanned = null;
            this.commandsSent = null;
            this.emptyTargets = null;
            this.routeFailures = null;
            this.targetAccepted = null;
            this.targetDuplicates = null;
            this.targetRejected = null;
            return;
        }
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

    static RoomFanoutMetrics noop() {
        return new RoomFanoutMetrics((MeterRegistry) null);
    }

    void ownerEventConsumed() {
        increment(ownerEventsConsumed);
    }

    void routesPlanned(int count) {
        if (routesPlanned != null && count > 0) {
            routesPlanned.increment(count);
        }
    }

    void commandSent() {
        increment(commandsSent);
    }

    void emptyTargetSet() {
        increment(emptyTargets);
    }

    void routeFailed() {
        increment(routeFailures);
    }

    void targetAccepted() {
        increment(targetAccepted);
    }

    void targetDuplicate() {
        increment(targetDuplicates);
    }

    void targetRejected() {
        increment(targetRejected);
    }

    private static Counter targetResultCounter(MeterRegistry meterRegistry, String result) {
        return Counter.builder("im_room_fanout_target_results")
                .tag("result", result)
                .register(meterRegistry);
    }

    private static void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
