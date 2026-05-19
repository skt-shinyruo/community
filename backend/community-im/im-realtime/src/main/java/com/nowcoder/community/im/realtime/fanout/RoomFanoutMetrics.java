package com.nowcoder.community.im.realtime.fanout;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RoomFanoutMetrics {

    private final Counter legacyEventsConsumed;
    private final Counter ownerEventsConsumed;
    private final Counter routesPlanned;
    private final Counter commandsSent;
    private final Counter emptyTargets;
    private final Counter routeFailures;

    @Autowired
    public RoomFanoutMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    RoomFanoutMetrics(MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            this.legacyEventsConsumed = null;
            this.ownerEventsConsumed = null;
            this.routesPlanned = null;
            this.commandsSent = null;
            this.emptyTargets = null;
            this.routeFailures = null;
            return;
        }
        this.legacyEventsConsumed = Counter.builder("im_room_fanout_events_consumed")
                .tag("path", "legacy")
                .register(meterRegistry);
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
    }

    static RoomFanoutMetrics noop() {
        return new RoomFanoutMetrics((MeterRegistry) null);
    }

    void legacyEventConsumed() {
        increment(legacyEventsConsumed);
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

    private static void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
