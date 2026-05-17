package com.nowcoder.community.gateway.canary;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanaryInstanceFilterTest {

    @Test
    void selectsInstancesByReleaseTrackAndTrafficGroup() {
        CanaryRouteProperties.Selector selector = new CanaryRouteProperties.Selector();
        selector.getMetadata().put("release.track", "canary");
        selector.getMetadata().put("traffic.group", "beta");

        List<ServiceInstance> selected = new CanaryInstanceFilter().filter(List.of(
                instance("stable-1", Map.of("release.track", "stable", "traffic.group", "baseline")),
                instance("canary-1", Map.of("release.track", "canary", "traffic.group", "beta"))
        ), selector);

        assertThat(selected).extracting(ServiceInstance::getInstanceId).containsExactly("canary-1");
    }

    @Test
    void excludesDrainingInstances() {
        CanaryRouteProperties.Selector selector = new CanaryRouteProperties.Selector();
        selector.getMetadata().put("release.track", "canary");

        List<ServiceInstance> selected = new CanaryInstanceFilter().filter(List.of(
                instance("canary-draining", Map.of("release.track", "canary", "draining", "true")),
                instance("canary-ready", Map.of("release.track", "canary", "draining", "false"))
        ), selector);

        assertThat(selected).extracting(ServiceInstance::getInstanceId).containsExactly("canary-ready");
    }

    @Test
    void returnsEmptyWhenInstancesAreNullOrEmpty() {
        CanaryInstanceFilter filter = new CanaryInstanceFilter();

        assertThat(filter.filter(null, null)).isEmpty();
        assertThat(filter.filter(List.of(), null)).isEmpty();
    }

    @Test
    void nullSelectorAllowsNonDrainingInstances() {
        List<ServiceInstance> selected = new CanaryInstanceFilter().filter(List.of(
                instance("ready", Map.of("release.track", "stable")),
                instance("draining", Map.of("draining", "true"))
        ), null);

        assertThat(selected).extracting(ServiceInstance::getInstanceId).containsExactly("ready");
    }

    @Test
    void nullMetadataDoesNotThrowAndDoesNotMatchRequiredMetadata() {
        CanaryRouteProperties.Selector selector = new CanaryRouteProperties.Selector();
        selector.getMetadata().put("release.track", "canary");

        List<ServiceInstance> selected = new CanaryInstanceFilter().filter(List.of(
                instanceWithNullMetadata("null-metadata"),
                instance("canary-ready", Map.of("release.track", "canary"))
        ), selector);

        assertThat(selected).extracting(ServiceInstance::getInstanceId).containsExactly("canary-ready");
    }

    @Test
    void excludesDrainingInstancesWhenValueHasWhitespace() {
        List<ServiceInstance> selected = new CanaryInstanceFilter().filter(List.of(
                instance("canary-draining", Map.of("draining", " true ")),
                instance("canary-ready", Map.of("draining", "false"))
        ), null);

        assertThat(selected).extracting(ServiceInstance::getInstanceId).containsExactly("canary-ready");
    }

    @Test
    void selectorMetadataNullValueDoesNotThrowAndYieldsNoMatches() {
        CanaryRouteProperties.Selector selector = new CanaryRouteProperties.Selector();
        selector.getMetadata().put("release.track", null);

        List<ServiceInstance> selected = new CanaryInstanceFilter().filter(List.of(
                instance("canary-ready", Map.of("release.track", "canary"))
        ), selector);

        assertThat(selected).isEmpty();
    }

    @Test
    void nullDrainingMetadataValueIsTreatedAsNonDraining() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("draining", null);

        List<ServiceInstance> selected = new CanaryInstanceFilter().filter(List.of(
                instance("ready-with-null-draining", metadata)
        ), null);

        assertThat(selected).extracting(ServiceInstance::getInstanceId).containsExactly("ready-with-null-draining");
    }

    private static ServiceInstance instance(String id, Map<String, String> metadata) {
        DefaultServiceInstance instance = new DefaultServiceInstance(id, "community-app", "127.0.0.1", 8080, false);
        instance.getMetadata().putAll(metadata);
        return instance;
    }

    private static ServiceInstance instanceWithNullMetadata(String id) {
        return new ServiceInstance() {
            @Override
            public String getServiceId() {
                return "community-app";
            }

            @Override
            public String getHost() {
                return "127.0.0.1";
            }

            @Override
            public int getPort() {
                return 8080;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public URI getUri() {
                return URI.create("http://127.0.0.1:8080");
            }

            @Override
            public Map<String, String> getMetadata() {
                return null;
            }

            @Override
            public String getInstanceId() {
                return id;
            }
        };
    }
}
