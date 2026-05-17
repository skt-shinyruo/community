package com.nowcoder.community.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayConfigRefreshListenerTest {

    @Test
    void publishesRefreshRoutesEventAfterRefreshScopeWhenGatewayKeysChange() {
        AtomicReference<Object> event = new AtomicReference<>();
        ApplicationEventPublisher publisher = event::set;
        GatewayConfigRefreshListener listener = new GatewayConfigRefreshListener(publisher);

        listener.onEnvironmentChange(Set.of("gateway.http.routes[0].service-id"));

        assertThat(event.get()).isNull();

        listener.onRefreshScopeRefreshed();

        assertThat(event.get()).isInstanceOf(RefreshRoutesEvent.class);
    }

    @Test
    void ignoresUnrelatedKeys() {
        AtomicReference<Object> event = new AtomicReference<>();
        ApplicationEventPublisher publisher = event::set;
        GatewayConfigRefreshListener listener = new GatewayConfigRefreshListener(publisher);

        listener.onEnvironmentChange(Set.of("security.jwt.issuer"));

        assertThat(event.get()).isNull();
    }

    @Test
    void ignoresRouteLikePrefixesWithoutBoundary() {
        AtomicReference<Object> event = new AtomicReference<>();
        ApplicationEventPublisher publisher = event::set;
        GatewayConfigRefreshListener listener = new GatewayConfigRefreshListener(publisher);

        listener.onEnvironmentChange(Set.of(
                "gateway.http.routes-extra",
                "gateway.im-edge-debug",
                "gateway.http.canarying"
        ));

        assertThat(event.get()).isNull();
    }

    @Test
    void publishesOnlyOneRefreshRoutesEventForMultipleGatewayChangesBeforeRefreshScopeCompletes() {
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;
        GatewayConfigRefreshListener listener = new GatewayConfigRefreshListener(publisher);

        listener.onEnvironmentChange(Set.of("gateway.http.routes"));
        listener.onEnvironmentChange(Set.of("gateway.im-edge.service-id", "gateway.http.canary.rules[0].service-id"));

        assertThat(events).isEmpty();

        listener.onRefreshScopeRefreshed();
        listener.onRefreshScopeRefreshed();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(RefreshRoutesEvent.class);
    }
}
