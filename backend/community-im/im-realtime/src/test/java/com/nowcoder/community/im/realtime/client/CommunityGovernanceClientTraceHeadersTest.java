package com.nowcoder.community.im.realtime.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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

class CommunityGovernanceClientTraceHeadersTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void validateSendPrivateMessageShouldForwardTraceHeaders() throws Exception {
        LinkedBlockingQueue<Map<String, String>> requests = new LinkedBlockingQueue<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/im-governance/private-messages/validate", exchange -> {
            requests.offer(captureHeaders(exchange));
            writeJson(exchange, 200, "{\"code\":0,\"message\":\"OK\"}");
        });
        server.start();

        CommunityGovernanceClient client = new CommunityGovernanceClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                1500L
        );

        String traceId = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
        CommunityGovernanceClient.Decision decision = client
                .validateSendPrivateMessage("bearer-token", 200, traceId)
                .block();

        assertThat(decision).isNotNull();
        assertThat(decision.allowed()).isTrue();

        Map<String, String> requestHeaders = requests.poll(5, TimeUnit.SECONDS);
        assertThat(requestHeaders).isNotNull();
        assertThat(requestHeaders).containsEntry("X-Trace-Id", traceId);
        assertThat(requestHeaders.get("traceparent"))
                .startsWith("00-" + traceId + "-")
                .endsWith("-01");
    }

    private static Map<String, String> captureHeaders(HttpExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", firstHeader(exchange, "Authorization"));
        headers.put("X-Trace-Id", firstHeader(exchange, "X-Trace-Id"));
        headers.put("traceparent", firstHeader(exchange, "traceparent"));
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
