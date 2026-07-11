package com.nowcoder.community.im.realtime.presence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Component
public class RoomPresenceHeartbeat implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RoomPresenceHeartbeat.class);

    private final Disposable ticker;

    public RoomPresenceHeartbeat(
            RoomLocalPresenceService roomLocalPresenceService,
            RoomPresenceProperties properties
    ) {
        this.ticker = Flux.interval(properties.normalizedHeartbeatInterval())
                .onBackpressureDrop()
                .subscribe(tick -> refreshSafely(roomLocalPresenceService));
    }

    private void refreshSafely(RoomLocalPresenceService roomLocalPresenceService) {
        try {
            roomLocalPresenceService.refreshPresence();
        } catch (RuntimeException ex) {
            log.warn("[room-presence] heartbeat refresh failed: {}", ex.toString());
        }
    }

    @Override
    public void destroy() {
        if (ticker != null) {
            ticker.dispose();
        }
    }
}
