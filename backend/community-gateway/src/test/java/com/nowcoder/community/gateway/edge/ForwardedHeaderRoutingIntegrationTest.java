package com.nowcoder.community.gateway.edge;

import com.nowcoder.community.gateway.CommunityGatewayApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                CommunityGatewayApplication.class,
                ForwardedHeaderRoutingIntegrationTest.PermitAllSecurityConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ForwardedHeaderRoutingIntegrationTest {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String FORWARDED = "Forwarded";
    private static final BlockingQueue<ForwardingHeaders> CAPTURES = new LinkedBlockingQueue<>();

    private static volatile DisposableServer downstreamServer;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.http.routes[0].id", () -> "bootstrap-api");
        registry.add("gateway.http.routes[0].path-prefix", () -> "/api");
        registry.add("gateway.http.routes[0].service-id", () -> "community-app");
        registry.add("spring.cloud.discovery.client.simple.instances.community-app[0].uri",
                ForwardedHeaderRoutingIntegrationTest::downstreamBaseUrl);
        registry.add("spring.cloud.gateway.discovery.locator.enabled", () -> "false");
        registry.add("spring.cloud.gateway.forwarded.enabled", () -> "false");
        registry.add("spring.cloud.gateway.x-forwarded.enabled", () -> "false");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("gateway.trusted-proxy.enabled", () -> "true");
        registry.add("gateway.trusted-proxy.cidrs[0]", () -> "127.0.0.0/8");
        registry.add("gateway.trusted-proxy.cidrs[1]", () -> "::1/128");
        registry.add("gateway.trusted-proxy.cidrs[2]", () -> "10.0.0.0/8");
    }

    @AfterAll
    static void stopServer() {
        if (downstreamServer != null) {
            downstreamServer.disposeNow();
            downstreamServer = null;
        }
    }

    @Test
    void shouldRouteOnlyCanonicalForwardingHeaderToDownstream() throws Exception {
        CAPTURES.clear();

        webTestClient.get()
                .uri("/api/posts")
                .header(FORWARDED, "for=203.0.113.7;proto=https", "for=198.51.100.77")
                .header(X_FORWARDED_FOR, "203.0.113.99, 198.51.100.77", "10.0.0.8")
                .header(X_REAL_IP, "203.0.113.100", "198.51.100.100")
                .exchange()
                .expectStatus().isOk();

        ForwardingHeaders capture = CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.xForwardedFor()).containsExactly("198.51.100.77");
        assertThat(capture.forwarded()).isEmpty();
        assertThat(capture.xRealIp()).isEmpty();
    }

    @Test
    void shouldDisableGatewayBuiltInForwardingHeaderFilters() {
        assertThat(applicationContext.getBeansOfType(ForwardedHeadersFilter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(XForwardedHeadersFilter.class)).isEmpty();
    }

    private static synchronized String downstreamBaseUrl() {
        if (downstreamServer == null) {
            downstreamServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle((request, response) -> {
                        CAPTURES.add(new ForwardingHeaders(
                                List.copyOf(request.requestHeaders().getAll(FORWARDED)),
                                List.copyOf(request.requestHeaders().getAll(X_FORWARDED_FOR)),
                                List.copyOf(request.requestHeaders().getAll(X_REAL_IP))
                        ));
                        return response.header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .sendString(Mono.just("{\"ok\":true}"))
                                .then();
                    })
                    .bindNow(Duration.ofSeconds(5));
        }
        return "http://127.0.0.1:" + downstreamServer.port();
    }

    private record ForwardingHeaders(
            List<String> forwarded,
            List<String> xForwardedFor,
            List<String> xRealIp
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebFluxSecurity
    static class PermitAllSecurityConfig {

        @Bean
        SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
            return http
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                    .build();
        }
    }
}
