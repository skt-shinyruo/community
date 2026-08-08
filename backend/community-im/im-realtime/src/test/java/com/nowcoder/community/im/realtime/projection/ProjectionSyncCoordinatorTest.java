package com.nowcoder.community.im.realtime.projection;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectionSyncCoordinatorTest {

    @Test
    void bootstrapOnStartup_shouldRetryFailedRefreshUntilProjectionsRecover() {
        MembershipProjectionService membershipProjectionService = mock(MembershipProjectionService.class);
        PolicyProjectionService policyProjectionService = mock(PolicyProjectionService.class);
        AtomicInteger policyAttempts = new AtomicInteger();
        when(membershipProjectionService.refreshNow()).thenAnswer(ignored -> Mono.empty());
        when(policyProjectionService.refreshNow()).thenAnswer(ignored ->
                policyAttempts.incrementAndGet() == 1
                        ? Mono.error(new IllegalStateException("policy snapshot unavailable"))
                        : Mono.empty());

        ProjectionSyncCoordinator coordinator =
                new ProjectionSyncCoordinator(membershipProjectionService, policyProjectionService);
        ReflectionTestUtils.setField(coordinator, "bootstrapOnStartup", true);

        coordinator.bootstrapOnStartup();

        assertThat(coordinator.ready()).isFalse();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(policyAttempts).hasValue(2);
            assertThat(coordinator.ready()).isTrue();
        });
    }

    @Test
    void bootstrapOnStartup_shouldNotOverlapAttemptsWhenStartupEventRepeats() {
        MembershipProjectionService membershipProjectionService = mock(MembershipProjectionService.class);
        PolicyProjectionService policyProjectionService = mock(PolicyProjectionService.class);
        Sinks.Empty<Void> membershipRefresh = Sinks.empty();
        AtomicInteger subscriptions = new AtomicInteger();
        when(membershipProjectionService.refreshNow()).thenReturn(Mono.defer(() -> {
            subscriptions.incrementAndGet();
            return membershipRefresh.asMono();
        }));
        when(policyProjectionService.refreshNow()).thenReturn(Mono.empty());

        ProjectionSyncCoordinator coordinator =
                new ProjectionSyncCoordinator(membershipProjectionService, policyProjectionService);
        ReflectionTestUtils.setField(coordinator, "bootstrapOnStartup", true);

        coordinator.bootstrapOnStartup();
        coordinator.bootstrapOnStartup();

        assertThat(subscriptions).hasValue(1);
        assertThat(coordinator.ready()).isFalse();
        membershipRefresh.tryEmitEmpty();
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(coordinator.ready()).isTrue());
    }

    @Test
    void bootstrapOnStartup_shouldCancelPendingRetryWhenApplicationContextCloses() {
        MembershipProjectionService membershipProjectionService = mock(MembershipProjectionService.class);
        PolicyProjectionService policyProjectionService = mock(PolicyProjectionService.class);
        AtomicInteger policyAttempts = new AtomicInteger();
        when(membershipProjectionService.refreshNow()).thenReturn(Mono.empty());
        when(policyProjectionService.refreshNow()).thenAnswer(ignored ->
                policyAttempts.incrementAndGet() == 1
                        ? Mono.error(new IllegalStateException("policy snapshot unavailable"))
                        : Mono.empty());

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
        context.registerBean(ProjectionSyncCoordinator.class, () ->
                new ProjectionSyncCoordinator(membershipProjectionService, policyProjectionService));
        context.refresh();
        ProjectionSyncCoordinator coordinator = context.getBean(ProjectionSyncCoordinator.class);
        ReflectionTestUtils.setField(coordinator, "bootstrapOnStartup", true);
        ReflectionTestUtils.setField(coordinator, "bootstrapRetryInitialBackoff", Duration.ofMillis(20));
        ReflectionTestUtils.setField(coordinator, "bootstrapRetryMaxBackoff", Duration.ofMillis(20));

        coordinator.bootstrapOnStartup();
        assertThat(policyAttempts).hasValue(1);
        context.close();

        await().pollDelay(Duration.ofMillis(100)).atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(policyAttempts).hasValue(1));
    }

    @Test
    void bootstrapOnStartup_shouldCancelInFlightRefreshWhenApplicationContextCloses() {
        MembershipProjectionService membershipProjectionService = mock(MembershipProjectionService.class);
        PolicyProjectionService policyProjectionService = mock(PolicyProjectionService.class);
        AtomicInteger cancellations = new AtomicInteger();
        when(membershipProjectionService.refreshNow()).thenReturn(
                Mono.<Void>never().doOnCancel(cancellations::incrementAndGet));
        when(policyProjectionService.refreshNow()).thenReturn(Mono.empty());

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
        context.registerBean(ProjectionSyncCoordinator.class, () ->
                new ProjectionSyncCoordinator(membershipProjectionService, policyProjectionService));
        context.refresh();
        ProjectionSyncCoordinator coordinator = context.getBean(ProjectionSyncCoordinator.class);
        ReflectionTestUtils.setField(coordinator, "bootstrapOnStartup", true);

        coordinator.bootstrapOnStartup();
        assertThat(coordinator.ready()).isFalse();
        context.close();

        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(cancellations).hasValue(1));
        assertThat(coordinator.ready()).isFalse();
    }

    @Test
    void refreshNow_shouldCoalesceConcurrentSubscribersUntilAttemptTerminates() {
        MembershipProjectionService membershipProjectionService = mock(MembershipProjectionService.class);
        PolicyProjectionService policyProjectionService = mock(PolicyProjectionService.class);
        Sinks.Empty<Void> membershipRefresh = Sinks.empty();
        AtomicInteger subscriptions = new AtomicInteger();
        when(membershipProjectionService.refreshNow()).thenReturn(Mono.defer(() -> {
            subscriptions.incrementAndGet();
            return membershipRefresh.asMono();
        }));
        when(policyProjectionService.refreshNow()).thenReturn(Mono.empty());

        ProjectionSyncCoordinator coordinator =
                new ProjectionSyncCoordinator(membershipProjectionService, policyProjectionService);

        StepVerifier.create(Mono.when(coordinator.refreshNow(), coordinator.refreshNow()))
                .then(() -> assertThat(subscriptions).hasValue(1))
                .then(membershipRefresh::tryEmitEmpty)
                .verifyComplete();

        assertThat(coordinator.ready()).isTrue();
    }

    @Test
    void ready_shouldRemainFalseUntilBothMembershipAndPolicyRefreshSucceed() {
        MembershipProjectionService membershipProjectionService = mock(MembershipProjectionService.class);
        PolicyProjectionService policyProjectionService = mock(PolicyProjectionService.class);
        when(membershipProjectionService.refreshNow())
                .thenReturn(Mono.empty(), Mono.empty());
        when(policyProjectionService.refreshNow())
                .thenReturn(Mono.error(new IllegalStateException("policy snapshot unavailable")), Mono.empty());

        ProjectionSyncCoordinator coordinator =
                new ProjectionSyncCoordinator(membershipProjectionService, policyProjectionService);

        assertThat(coordinator.ready()).isFalse();
        assertThatThrownBy(coordinator::requireReady)
                .isInstanceOf(ResponseStatusException.class);

        StepVerifier.create(coordinator.refreshNow())
                .expectErrorMessage("policy snapshot unavailable")
                .verify();

        assertThat(coordinator.ready()).isFalse();

        StepVerifier.create(coordinator.refreshNow())
                .verifyComplete();

        assertThat(coordinator.ready()).isTrue();
        assertThatCode(coordinator::requireReady).doesNotThrowAnyException();
    }

    @Test
    void refreshNow_shouldCallBothProjectionServicesAndFlipReady() {
        MembershipProjectionService membershipProjectionService = mock(MembershipProjectionService.class);
        PolicyProjectionService policyProjectionService = mock(PolicyProjectionService.class);
        when(membershipProjectionService.refreshNow()).thenReturn(Mono.empty());
        when(policyProjectionService.refreshNow()).thenReturn(Mono.empty());

        ProjectionSyncCoordinator coordinator =
                new ProjectionSyncCoordinator(membershipProjectionService, policyProjectionService);

        StepVerifier.create(coordinator.refreshNow())
                .verifyComplete();

        assertThat(coordinator.ready()).isTrue();
        verify(membershipProjectionService).refreshNow();
        verify(policyProjectionService).refreshNow();
    }
}
