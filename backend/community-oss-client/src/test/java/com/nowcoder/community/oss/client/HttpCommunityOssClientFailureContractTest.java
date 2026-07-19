package com.nowcoder.community.oss.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class HttpCommunityOssClientFailureContractTest {

    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000007301");

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void metadata404MustExposeTypedNotFoundFailure() throws Exception {
        Throwable failure = callMetadata(errorServer(404, 18404, "object not found"));

        assertTypedFailure(failure, "NOT_FOUND", 404, false);
    }

    @Test
    void metadata409MustExposeTypedConflictFailure() throws Exception {
        Throwable failure = callMetadata(errorServer(409, 18409, "metadata conflict"));

        assertTypedFailure(failure, "CONFLICT", 409, false);
    }

    @Test
    void metadata5xxMustExposeTypedTransientFailure() throws Exception {
        Throwable failure = callMetadata(errorServer(503, 18503, "temporarily unavailable"));

        assertTypedFailure(failure, "TRANSIENT", 503, true);
    }

    @Test
    void readTimeoutMustBeDistinctFromHttpAndBadEnvelopeFailures() throws Exception {
        HttpServer server = startServer(exchange -> {
            try {
                Thread.sleep(500L);
                byte[] body = successMetadataEnvelope().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(100);
        requestFactory.setReadTimeout(50);
        HttpCommunityOssClient client = new HttpCommunityOssClient(
                baseUrl(server),
                RestClient.builder().requestFactory(requestFactory),
                () -> "service-token-1"
        );

        Throwable failure = catchThrowable(() -> client.getMetadata(OBJECT_ID));

        assertTypedFailure(failure, "TIMEOUT", 0, true);
    }

    @Test
    void malformedSuccessEnvelopeMustExposeTypedBadResponseFailure() throws Exception {
        HttpServer server = responseServer(200, "{\"data\":{}}");

        Throwable failure = callMetadata(server);

        assertTypedFailure(failure, "BAD_RESPONSE", 200, false);
    }

    @Test
    void blankServiceTokenMustFailClosedBeforeSendingHttp() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            requestCount.incrementAndGet();
            exchange.close();
        });
        HttpCommunityOssClient client = new HttpCommunityOssClient(baseUrl(server), () -> "   ");

        Throwable failure = catchThrowable(() -> client.getMetadata(OBJECT_ID));

        assertTypedFailure(failure, "BAD_RESPONSE", 0, false);
        assertThat(failure.getMessage()).isEqualTo("OSS service authentication unavailable");
        assertThat(failure.getCause()).isNull();
        assertThat(requestCount).hasValue(0);
    }

    @Test
    void tokenProviderExceptionMustBeSanitizedAndFailBeforeSendingHttp() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            requestCount.incrementAndGet();
            exchange.close();
        });
        HttpCommunityOssClient client = new HttpCommunityOssClient(baseUrl(server), () -> {
            throw new IllegalStateException("could not sign secret-service-token-7");
        });

        Throwable failure = catchThrowable(() -> client.getMetadata(OBJECT_ID));

        assertTypedFailure(failure, "BAD_RESPONSE", 0, false);
        assertThat(failure.getMessage())
                .isEqualTo("OSS service authentication unavailable")
                .doesNotContain("secret-service-token-7", "could not sign");
        assertThat(failure.getCause()).isNull();
        assertThat(requestCount).hasValue(0);
    }

    private Throwable callMetadata(HttpServer server) {
        HttpCommunityOssClient client = new HttpCommunityOssClient(baseUrl(server), () -> "service-token-1");
        return catchThrowable(() -> client.getMetadata(OBJECT_ID));
    }

    private HttpServer errorServer(int status, int code, String message) throws IOException {
        return responseServer(status, """
                {
                  "code": %d,
                  "message": "%s",
                  "httpStatus": %d,
                  "data": null,
                  "traceId": "trace-failure",
                  "timestamp": 1784073600000
                }
                """.formatted(code, message, status));
    }

    private HttpServer responseServer(int status, String response) throws IOException {
        return startServer(exchange -> {
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/oss/objects/" + OBJECT_ID, handler);
        server.start();
        servers.add(server);
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void assertTypedFailure(
            Throwable failure,
            String expectedCategory,
            int expectedHttpStatus,
            boolean expectedRetryable
    ) throws Exception {
        assertThat(failure).isNotNull();
        Class<?> failureType = Class.forName("com.nowcoder.community.oss.client.OssClientException");
        assertThat(failureType.isInstance(failure))
                .as("raw RestClient/IllegalStateException failures must not cross the OSS client boundary")
                .isTrue();
        Method category = failureType.getMethod("category");
        Method httpStatus = failureType.getMethod("httpStatus");
        Method retryable = failureType.getMethod("retryable");

        assertThat(String.valueOf(category.invoke(failure))).isEqualTo(expectedCategory);
        assertThat(httpStatus.invoke(failure)).isEqualTo(expectedHttpStatus);
        assertThat(retryable.invoke(failure)).isEqualTo(expectedRetryable);
        assertThat(failure.getMessage()).isNotBlank();
    }

    private static String successMetadataEnvelope() {
        return """
                {
                  "code": 0,
                  "message": "OK",
                  "httpStatus": 200,
                  "data": {
                    "objectId": "00000000-0000-7000-8000-000000007301",
                    "currentVersionId": "00000000-0000-7000-8000-000000007302",
                    "usage": "CONTENT_POST_MEDIA",
                    "ownerService": "community-app",
                    "ownerDomain": "content",
                    "ownerType": "post-media-draft",
                    "ownerId": "asset-7",
                    "visibility": "PUBLIC",
                    "status": "ACTIVE",
                    "fileName": "post.png",
                    "contentType": "image/png",
                    "contentLength": 4,
                    "checksumSha256": "sha256-post",
                    "publicUrl": "https://cdn.example.test/post.png"
                  }
                }
                """;
    }
}
