package com.nowcoder.community.im.realtime.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ImCoreClientTraceHeadersTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void listAllRoomIdsForUserShouldSendAuthTraceAndPagingParams() throws Exception {
        LinkedBlockingQueue<Map<String, String>> requests = new LinkedBlockingQueue<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/im/realtime/users/42/rooms", exchange -> {
            requests.offer(captureRequest(exchange));
            writeJson(exchange, 200, "{\"roomIds\":[101,102],\"nextCursorExclusive\":0,\"hasMore\":false}");
        });
        server.start();

        ImCoreClient client = new ImCoreClient(
                WebClient.builder()
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .build()
        );

        List<Long> roomIds = client.listAllRoomIdsForUser(42, "token-123", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .collectList()
                .block();

        assertThat(roomIds).containsExactly(101L, 102L);

        Map<String, String> request = requests.poll(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request).containsEntry("Authorization", "Bearer token-123");
        assertThat(request).containsEntry("X-Trace-Id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(request.get("traceparent"))
                .startsWith("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-")
                .endsWith("-01");
        assertThat(request.get("query")).contains("cursor=0");
        assertThat(request.get("query")).contains("limit=1000");
    }

    private static Map<String, String> captureRequest(HttpExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", firstHeader(exchange, "Authorization"));
        headers.put("X-Trace-Id", firstHeader(exchange, "X-Trace-Id"));
        headers.put("traceparent", firstHeader(exchange, "traceparent"));
        headers.put("query", exchange.getRequestURI() == null ? "" : String.valueOf(exchange.getRequestURI().getRawQuery()));
        return headers;
    }

    private static String firstHeader(HttpExchange exchange, String name) {
        if (exchange == null || exchange.getRequestHeaders() == null || name == null) {
            return "";
        }
        List<String> values = exchange.getRequestHeaders().get(name);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return "";
        }
        return values.get(0);
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
