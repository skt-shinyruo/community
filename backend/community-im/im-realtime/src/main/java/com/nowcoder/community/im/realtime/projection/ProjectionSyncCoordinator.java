package com.nowcoder.community.im.realtime.projection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ProjectionSyncCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ProjectionSyncCoordinator.class);

    private final MembershipProjectionService membershipProjectionService;
    private final PolicyProjectionService policyProjectionService;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Value("${im.projection.bootstrap-on-startup:true}")
    private boolean bootstrapOnStartup;

    public ProjectionSyncCoordinator(
            MembershipProjectionService membershipProjectionService,
            PolicyProjectionService policyProjectionService
    ) {
        this.membershipProjectionService = membershipProjectionService;
        this.policyProjectionService = policyProjectionService;
    }

    public Mono<Void> refreshNow() {
        ready.set(false);
        return Mono.whenDelayError(
                        membershipProjectionService.refreshNow(),
                        policyProjectionService.refreshNow()
                )
                .doOnSuccess(ignored -> ready.set(true))
                .doOnError(error -> ready.set(false));
    }

    public boolean ready() {
        return ready.get();
    }

    public void requireReady() {
        if (!ready()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "IM projections are not ready");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapOnStartup() {
        if (!bootstrapOnStartup) {
            return;
        }
        refreshNow()
                .doOnError(error -> log.warn("projection bootstrap failed", error))
                .subscribe();
    }
}
