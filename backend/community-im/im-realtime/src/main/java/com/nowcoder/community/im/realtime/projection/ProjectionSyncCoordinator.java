package com.nowcoder.community.im.realtime.projection;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ProjectionSyncCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ProjectionSyncCoordinator.class);

    private final MembershipProjectionService membershipProjectionService;
    private final PolicyProjectionService policyProjectionService;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean bootstrapStarted = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicReference<Disposable> bootstrapSubscription = new AtomicReference<>();
    private final Object refreshMonitor = new Object();

    private Mono<Void> refreshInFlight;

    @Value("${im.projection.bootstrap-on-startup:true}")
    private boolean bootstrapOnStartup = true;

    @Value("${im.projection.bootstrap-retry-initial-backoff:PT1S}")
    private Duration bootstrapRetryInitialBackoff = Duration.ofSeconds(1);

    @Value("${im.projection.bootstrap-retry-max-backoff:PT30S}")
    private Duration bootstrapRetryMaxBackoff = Duration.ofSeconds(30);

    public ProjectionSyncCoordinator(
            MembershipProjectionService membershipProjectionService,
            PolicyProjectionService policyProjectionService
    ) {
        this.membershipProjectionService = membershipProjectionService;
        this.policyProjectionService = policyProjectionService;
    }

    public Mono<Void> refreshNow() {
        return Mono.defer(() -> {
            synchronized (refreshMonitor) {
                if (refreshInFlight == null) {
                    ready.set(false);
                    AtomicReference<Mono<Void>> attemptReference = new AtomicReference<>();
                    Mono<Void> sharedRefresh = Mono.whenDelayError(
                                            Mono.defer(membershipProjectionService::refreshNow),
                                            Mono.defer(policyProjectionService::refreshNow)
                                    )
                                    .share();
                    Mono<Void> attempt = sharedRefresh
                            .doOnSuccess(ignored -> completeRefreshAttempt(attemptReference.get(), true))
                            .doOnError(error -> completeRefreshAttempt(attemptReference.get(), false));
                    attemptReference.set(attempt);
                    refreshInFlight = attempt;
                }
                return refreshInFlight;
            }
        });
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
        if (!bootstrapOnStartup || stopped.get() || !bootstrapStarted.compareAndSet(false, true)) {
            return;
        }
        Disposable subscription = Mono.defer(this::refreshNow)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, retryInitialBackoff())
                        .maxBackoff(retryMaxBackoff())
                        .doBeforeRetry(retry -> log.warn(
                                "projection bootstrap failed; retrying (retry={})",
                                retry.totalRetries() + 1,
                                retry.failure()
                        )))
                .subscribe(
                        ignored -> {
                        },
                        error -> {
                            ready.set(false);
                            log.error("projection bootstrap stopped unexpectedly", error);
                        },
                        () -> log.info("projection bootstrap completed")
                );
        if (!bootstrapSubscription.compareAndSet(null, subscription) || stopped.get()) {
            subscription.dispose();
        }
    }

    @PreDestroy
    void stopBootstrap() {
        stopped.set(true);
        Disposable subscription = bootstrapSubscription.getAndSet(null);
        if (subscription != null) {
            subscription.dispose();
        }
        synchronized (refreshMonitor) {
            ready.set(false);
        }
    }

    private void completeRefreshAttempt(Mono<Void> attempt, boolean succeeded) {
        synchronized (refreshMonitor) {
            if (refreshInFlight == attempt) {
                ready.set(succeeded && !stopped.get());
                refreshInFlight = null;
            }
        }
    }

    private Duration retryInitialBackoff() {
        if (bootstrapRetryInitialBackoff == null || bootstrapRetryInitialBackoff.isZero()
                || bootstrapRetryInitialBackoff.isNegative()) {
            return Duration.ofSeconds(1);
        }
        return bootstrapRetryInitialBackoff;
    }

    private Duration retryMaxBackoff() {
        Duration initialBackoff = retryInitialBackoff();
        if (bootstrapRetryMaxBackoff == null || bootstrapRetryMaxBackoff.compareTo(initialBackoff) < 0) {
            return initialBackoff;
        }
        return bootstrapRetryMaxBackoff;
    }
}
