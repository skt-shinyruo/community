package com.nowcoder.community.gateway.ws;

import com.nowcoder.community.gateway.CommunityGatewayApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                CommunityGatewayApplication.class,
                WsTransparentProxyIntegrationTest.PermitAllSecurityConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class WsTransparentProxyIntegrationTest {

    private static volatile DisposableServer workerServer;

    @Autowired
    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.ws.proxy.path", () -> "/ws/im/workers/**");
        registry.add("gateway.ws.discovery.service-id", () -> "im-realtime-worker");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].uri",
                () -> "http://127.0.0.1:" + workerPort());
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.workerId", () -> "worker-a");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPath", () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPort",
                () -> String.valueOf(workerPort()));
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("security.jwt.hmac-secret", () -> "gateway-test-jwt-secret-please-change-123456");
    }

    @AfterAll
    static void tearDown() {
        if (workerServer != null) {
            workerServer.disposeNow();
            workerServer = null;
        }
    }

    @Test
    void shouldProxyFramesToWorkerSelectedByPath() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch connected = new CountDownLatch(1);

        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI gatewayUri = URI.create("ws://localhost:" + port + "/ws/im/workers/worker-a");

        Disposable ws = client.execute(gatewayUri, session -> {
                    connected.countDown();
                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> recv = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .take(1)
                            .then();
                    return Mono.when(send, recv);
                })
                .subscribe();
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();
            outbound.tryEmitNext("hello");
            outbound.tryEmitComplete();
            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("worker-a:hello");
        } finally {
            ws.dispose();
        }
    }

    private static synchronized int workerPort() {
        if (workerServer == null) {
            workerServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .route(routes -> routes.ws("/internal/ws/im", (in, out) ->
                            out.sendString(in.receive().asString().map(text -> "worker-a:" + text))
                    ))
                    .bindNow(Duration.ofSeconds(5));
        }
        return workerServer.port();
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
