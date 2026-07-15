package com.nowcoder.community.oss.client;

import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCommunityOssClientTest {

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void stringConstructorForwardsCurrentBearerTokenToOssApiCalls() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = startUploadSessionServer(authorization, requestReceived, wrappedUploadSessionResponse());
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-a");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            HttpCommunityOssClient client = new HttpCommunityOssClient("http://127.0.0.1:" + server.getAddress().getPort());
            client.prepareUpload(new OssUploadSessionRequest(
                    "DRIVE_FILE",
                    "community-app",
                    "drive",
                    "drive-upload",
                    "7",
                    "PRIVATE",
                    "note.txt",
                    "text/plain",
                    2,
                    "",
                    "7"
            ));

            assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(authorization.get()).isEqualTo("Bearer token-a");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void prepareUploadShouldReadUnifiedResultWrapperResponses() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = startUploadSessionServer(authorization, requestReceived, wrappedUploadSessionResponse());
        try {
            HttpCommunityOssClient client = new HttpCommunityOssClient("http://127.0.0.1:" + server.getAddress().getPort());

            OssUploadSessionResponse response = client.prepareUpload(new OssUploadSessionRequest(
                    "DRIVE_FILE",
                    "community-app",
                    "drive",
                    "drive-upload",
                    "7",
                    "PRIVATE",
                    "note.txt",
                    "text/plain",
                    2,
                    "",
                    "7"
            ));

            assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(response.sessionId().toString()).isEqualTo("00000000-0000-7000-8000-000000000003");
            assertThat(response.objectId().toString()).isEqualTo("00000000-0000-7000-8000-000000000001");
            assertThat(response.versionId().toString()).isEqualTo("00000000-0000-7000-8000-000000000002");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getMetadataShouldReadOwnerFieldsFromOssService() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        UUID objectId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        HttpServer server = startMetadataServer(authorization, requestReceived, wrappedMetadataResponseWithOwnerFields(), objectId);
        try {
            HttpCommunityOssClient client = new HttpCommunityOssClient("http://127.0.0.1:" + server.getAddress().getPort());

            OssMetadataResponse response = client.getMetadata(objectId);

            assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(response.objectId()).isEqualTo(objectId);
            assertThat(response.currentVersionId().toString()).isEqualTo("00000000-0000-7000-8000-000000000002");
            assertThat(response.usage()).isEqualTo("DRIVE_FILE");
            assertThat(response.ownerService()).isEqualTo("community-app");
            assertThat(response.ownerDomain()).isEqualTo("drive");
            assertThat(response.ownerType()).isEqualTo("drive-upload");
            assertThat(response.ownerId()).isEqualTo("7");
            assertThat(response.visibility()).isEqualTo("PRIVATE");
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.fileName()).isEqualTo("note.txt");
            assertThat(response.contentType()).isEqualTo("text/plain");
            assertThat(response.contentLength()).isEqualTo(12);
            assertThat(response.checksumSha256()).isEmpty();
            assertThat(response.publicUrl()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void prepareUploadShouldWrapJsonCodecParseFailures() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = startUploadSessionServer(authorization, requestReceived, "{");
        try {
            HttpCommunityOssClient client = new HttpCommunityOssClient("http://127.0.0.1:" + server.getAddress().getPort());

            assertThatThrownBy(() -> client.prepareUpload(new OssUploadSessionRequest(
                    "DRIVE_FILE",
                    "community-app",
                    "drive",
                    "drive-upload",
                    "7",
                    "PRIVATE",
                    "note.txt",
                    "text/plain",
                    2,
                    "",
                    "7"
            )))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("failed to parse OSS response")
                    .hasCauseInstanceOf(JsonCodecException.class);
            assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void bindReferenceShouldSendCallerSuppliedIdSoResponseLossCanBeRetried() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID versionId = UUID.fromString("00000000-0000-7000-8000-000000000002");
        UUID referenceId = UUID.fromString("00000000-0000-7000-8000-000000000005");
        AtomicReference<String> requestBody = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = startBindReferenceServer(
                objectId, requestBody, requestReceived, wrappedReferenceResponse(objectId, versionId, referenceId));
        try {
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort());

            OssReferenceResponse response = client.bindObjectReference(objectId, new OssBindReferenceRequest(
                    referenceId.toString(),
                    versionId.toString(),
                    "community-app",
                    "content",
                    "post-media",
                    "post-7",
                    "PRIMARY",
                    Instant.parse("2026-05-07T01:00:00Z"),
                    "actor-7"
            ));

            assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(JsonMappers.standard().readTree(requestBody.get()).path("referenceId").asText())
                    .isEqualTo(referenceId.toString());
            assertThat(response.referenceId()).isEqualTo(referenceId);
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startUploadSessionServer(
            AtomicReference<String> authorization,
            CountDownLatch requestReceived,
            String responseJson
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/oss/objects/upload-sessions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            exchange.getRequestBody().readAllBytes();
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            requestReceived.countDown();
        });
        server.start();
        return server;
    }

    private static HttpServer startMetadataServer(
            AtomicReference<String> authorization,
            CountDownLatch requestReceived,
            String responseJson,
            UUID objectId
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/oss/objects/" + objectId, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            requestReceived.countDown();
        });
        server.start();
        return server;
    }

    private static HttpServer startBindReferenceServer(
            UUID objectId,
            AtomicReference<String> requestBody,
            CountDownLatch requestReceived,
            String responseJson
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/oss/objects/" + objectId + "/references", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            requestReceived.countDown();
        });
        server.start();
        return server;
    }

    private static String directUploadSessionResponse() {
        return """
                    {
                      "sessionId": "00000000-0000-7000-8000-000000000003",
                      "objectId": "00000000-0000-7000-8000-000000000001",
                      "versionId": "00000000-0000-7000-8000-000000000002",
                      "uploadMode": "PROXY",
                      "uploadUrl": "/api/oss/objects/00000000-0000-7000-8000-000000000001/complete",
                      "expiresAt": "2026-05-07T00:15:00Z"
                    }
                    """;
    }

    private static String wrappedUploadSessionResponse() {
        return """
                    {
                      "code": 0,
                      "message": "OK",
                      "httpStatus": 200,
                      "data": %s,
                      "traceId": "trace-1",
                      "timestamp": 1778396128900
                    }
                    """.formatted(directUploadSessionResponse());
    }

    private static String wrappedMetadataResponseWithOwnerFields() {
        return """
                    {
                      "code": 0,
                      "message": "OK",
                      "httpStatus": 200,
                      "data": {
                        "objectId": "00000000-0000-7000-8000-000000000001",
                        "currentVersionId": "00000000-0000-7000-8000-000000000002",
                        "usage": "DRIVE_FILE",
                        "ownerService": "community-app",
                        "ownerDomain": "drive",
                        "ownerType": "drive-upload",
                        "ownerId": "7",
                        "visibility": "PRIVATE",
                        "status": "ACTIVE",
                        "fileName": "note.txt",
                        "contentType": "text/plain",
                        "contentLength": 12,
                        "checksumSha256": "",
                        "publicUrl": null
                      },
                      "traceId": "trace-1",
                      "timestamp": 1778396128900
                    }
                """;
    }

    private static String wrappedReferenceResponse(UUID objectId, UUID versionId, UUID referenceId) {
        return """
                {
                  "code": 0,
                  "message": "OK",
                  "httpStatus": 200,
                  "data": {
                    "referenceId": "%s",
                    "objectId": "%s",
                    "versionId": "%s",
                    "subjectService": "community-app",
                    "subjectDomain": "content",
                    "subjectType": "post-media",
                    "subjectId": "post-7",
                    "referenceRole": "PRIMARY",
                    "status": "ACTIVE",
                    "retainUntil": "2026-05-07T01:00:00Z",
                    "createdAt": "2026-05-07T00:00:00Z",
                    "releasedAt": null
                  },
                  "traceId": "trace-1",
                  "timestamp": 1778396128900
                }
                """.formatted(referenceId, objectId, versionId);
    }
}
