package com.nowcoder.community.gateway;

import com.nowcoder.community.gateway.config.GatewayWebClientProperties;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(properties = {
        "gateway.webclient.response-timeout-ms=200",
        "gateway.webclient.connect-timeout-ms=1000"
})
class GatewayWebClientConfigTest {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private ReactorClientHttpConnector gatewayWebClientConnector;

    @Autowired
    private GatewayWebClientProperties properties;

    @Test
    void shouldApplyResponseTimeoutFromProperties() {
        assertThat(properties.getResponseTimeoutMs()).isEqualTo(200);

        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/delay", (req, res) ->
                        res.sendString(Mono.just("ok").delayElement(Duration.ofSeconds(5))).then()
                ))
                .bindNow();

        try {
            assertThat(webClientBuilder).isNotNull();

            WebClient client = WebClient.builder()
                    .clientConnector(gatewayWebClientConnector)
                    .build();
            Mono<String> call = client.get()
                    .uri("http://127.0.0.1:" + server.port() + "/delay")
                    .retrieve()
                    .bodyToMono(String.class);

            long startNanos = System.nanoTime();
            Throwable ex = catchThrowable(() -> call.block(Duration.ofSeconds(2)));
            assertThat(ex).isNotNull();
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            // response-timeout-ms=200：不应跑满 block 的 2s 超时
            assertThat(elapsedMs).isLessThan(1_500);

            boolean isTimeout = hasCause(ex, TimeoutException.class)
                    || hasCause(ex, ReadTimeoutException.class);
            assertThat(isTimeout).isTrue();
        } finally {
            server.disposeNow();
        }
    }

    private boolean hasCause(Throwable ex, Class<?> type) {
        Throwable cur = ex;
        while (cur != null) {
            if (type.isInstance(cur)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
