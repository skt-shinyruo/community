package com.nowcoder.community.gateway.config;

import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GatewayConfigRefreshListener {

    private final ApplicationEventPublisher publisher;
    private final AtomicBoolean routeRefreshPending = new AtomicBoolean(false);

    public GatewayConfigRefreshListener(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        onEnvironmentChange(event.getKeys());
    }

    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefreshScopeRefreshed(RefreshScopeRefreshedEvent event) {
        onRefreshScopeRefreshed();
    }

    void onEnvironmentChange(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        if (keys.stream().anyMatch(GatewayConfigRefreshListener::isGatewayRouteKey)) {
            routeRefreshPending.set(true);
        }
    }

    void onRefreshScopeRefreshed() {
        if (routeRefreshPending.compareAndSet(true, false)) {
            publisher.publishEvent(new RefreshRoutesEvent(this));
        }
    }

    private static boolean isGatewayRouteKey(String key) {
        return matchesPrefix(key, "gateway.http.routes")
                || matchesPrefix(key, "gateway.im-edge")
                || matchesPrefix(key, "gateway.http.canary");
    }

    private static boolean matchesPrefix(String key, String prefix) {
        return key != null
                && key.startsWith(prefix)
                && (key.length() == prefix.length()
                || key.charAt(prefix.length()) == '.'
                || key.charAt(prefix.length()) == '[');
    }
}
