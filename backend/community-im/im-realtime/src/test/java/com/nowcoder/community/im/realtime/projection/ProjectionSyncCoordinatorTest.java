package com.nowcoder.community.im.realtime.projection;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectionSyncCoordinatorTest {

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
