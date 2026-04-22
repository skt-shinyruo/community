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
import java.util.UUID;
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
        UUID userId = uuid(42);
        UUID roomId1 = uuid(101);
        UUID roomId2 = uuid(102);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/im/realtime/users/" + userId + "/rooms", exchange -> {
            requests.offer(captureRequest(exchange));
            writeJson(
                    exchange,
                    200,
                    "{\"roomIds\":[\"" + roomId1 + "\",\"" + roomId2 + "\"],\"nextCursorExclusive\":null,\"hasMore\":false}"
            );
        });
        server.start();

        ImCoreClient client = new ImCoreClient(
                WebClient.builder()
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .build()
        );

        List<UUID> roomIds = client.listAllRoomIdsForUser(userId, "token-123", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .collectList()
                .block();

        assertThat(roomIds).containsExactly(roomId1, roomId2);

        Map<String, String> request = requests.poll(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request).containsEntry("Authorization", "Bearer token-123");
        assertThat(request).containsEntry("X-Trace-Id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(request.get("traceparent"))
                .startsWith("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-")
                .endsWith("-01");
        assertThat(request.get("query")).contains("limit=1000");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
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
