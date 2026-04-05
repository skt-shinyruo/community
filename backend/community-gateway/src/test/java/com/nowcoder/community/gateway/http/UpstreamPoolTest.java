package com.nowcoder.community.gateway.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstreamPoolTest {

    @Test
    void shouldNormalizeUriAndUrisIntoOneOrderedPool() {
        UpstreamRouteProperties.Route route = new UpstreamRouteProperties.Route();
        route.setId("bootstrap-api");
        route.setUri(URI.create("http://127.0.0.1:8080"));
        route.getUris().add(URI.create("http://127.0.0.1:8081"));
        route.getUris().add(URI.create("http://127.0.0.1:8082"));

        UpstreamPool pool = UpstreamPool.from(route);

        assertThat(pool.routeId()).isEqualTo("bootstrap-api");
        assertThat(pool.upstreams()).containsExactly(
                URI.create("http://127.0.0.1:8080"),
                URI.create("http://127.0.0.1:8081"),
                URI.create("http://127.0.0.1:8082")
        );
    }

    @Test
    void shouldSelectUpstreamsRoundRobin() {
        UpstreamRouteProperties.Route route = new UpstreamRouteProperties.Route();
        route.setId("bootstrap-api");
        route.getUris().addAll(List.of(
                URI.create("http://127.0.0.1:8080"),
                URI.create("http://127.0.0.1:8081"),
                URI.create("http://127.0.0.1:8082")
        ));

        UpstreamPool pool = UpstreamPool.from(route);

        assertThat(pool.nextCandidates()).containsExactly(
                URI.create("http://127.0.0.1:8080"),
                URI.create("http://127.0.0.1:8081"),
                URI.create("http://127.0.0.1:8082")
        );
        assertThat(pool.nextCandidates()).containsExactly(
                URI.create("http://127.0.0.1:8081"),
                URI.create("http://127.0.0.1:8082"),
                URI.create("http://127.0.0.1:8080")
        );
        assertThat(pool.nextCandidates()).containsExactly(
                URI.create("http://127.0.0.1:8082"),
                URI.create("http://127.0.0.1:8080"),
                URI.create("http://127.0.0.1:8081")
        );
    }

    @Test
    void shouldRejectEmptyPool() {
        UpstreamRouteProperties.Route route = new UpstreamRouteProperties.Route();
        route.setId("bootstrap-api");

        assertThatThrownBy(() -> UpstreamPool.from(route))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one upstream");
    }
}
