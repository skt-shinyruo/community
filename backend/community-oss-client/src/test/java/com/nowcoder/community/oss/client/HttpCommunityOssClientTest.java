package com.nowcoder.community.oss.client;

import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssPublicFileResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCommunityOssClientTest {

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void backgroundRequestWithoutServletContextShouldUseServiceToken() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = startUploadSessionServer(authorization, requestReceived, wrappedUploadSessionResponse());
        try {
            RequestContextHolder.resetRequestAttributes();

            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    () -> "service-token-1"
            );
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
            assertThat(authorization.get()).isEqualTo("Bearer service-token-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void browserBearerShouldNeverReplaceServiceToken() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = startUploadSessionServer(authorization, requestReceived, wrappedUploadSessionResponse());
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer browser-token");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    () -> "service-token-1"
            );
            client.prepareUpload(uploadSessionRequest());

            assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(authorization.get()).isEqualTo("Bearer service-token-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publicFileShouldRemainAnonymousAndNeverCallTokenProvider() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = startPublicFileServer(authorization, requestReceived);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer browser-token");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    () -> {
                        throw new AssertionError("public file loading must not request a service token");
                    }
            );

            OssPublicFileResponse response = client.loadPublicFile("public.txt");

            assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(authorization.get()).isNull();
            assertThat(response.content()).isEqualTo("ok".getBytes(StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void callerDefaultAuthorizationShouldNotReachPublicFiles() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = startPublicFileServer(authorization, requestReceived);
        try {
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    RestClient.builder().defaultHeader(
                            HttpHeaders.AUTHORIZATION, "Bearer leaked-default"),
                    () -> {
                        throw new AssertionError("public file loading must not request a service token");
                    }
            );

            client.loadPublicFile("public.txt");

            assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(authorization.get()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void callerDefaultAuthorizationShouldNotReachPrefixedPublicFiles() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = startPublicFileServer(
                authorization, requestReceived, "/proxy/files/public.txt");
        try {
            AtomicInteger tokenCalls = new AtomicInteger();
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/proxy",
                    RestClient.builder().defaultHeader(
                            HttpHeaders.AUTHORIZATION, "Bearer leaked-default"),
                    () -> {
                        tokenCalls.incrementAndGet();
                        return "service-token-1";
                    }
            );

            client.loadPublicFile("public.txt");

            assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(authorization.get()).isNull();
            assertThat(tokenCalls).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void finalClientAuthenticationShouldOverrideCallerInterceptorByRoute() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID versionId = UUID.fromString("00000000-0000-7000-8000-000000000002");
        UUID referenceId = UUID.fromString("00000000-0000-7000-8000-000000000005");
        List<CapturedRequest> requests = new ArrayList<>();
        AtomicInteger tokenCalls = new AtomicInteger();
        HttpServer server = startCapabilityServer(requests, objectId, versionId, referenceId);
        try {
            RestClient.Builder callerBuilder = RestClient.builder()
                    .requestInterceptor((request, body, execution) -> {
                        request.getHeaders().set(
                                HttpHeaders.AUTHORIZATION, "Bearer leaked-interceptor");
                        return execution.execute(request, body);
                    });
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    callerBuilder,
                    () -> {
                        tokenCalls.incrementAndGet();
                        return "service-token-1";
                    }
            );

            client.loadPublicFile("public.txt");
            client.prepareUpload(uploadSessionRequest());

            assertThat(requests).extracting(CapturedRequest::authorization)
                    .containsExactly(
                            List.of(),
                            List.of("Bearer service-token-1")
                    );
            assertThat(tokenCalls).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void everyNonPublicCapabilityShouldUseInternalRouteAndExactlyOneServiceToken() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID versionId = UUID.fromString("00000000-0000-7000-8000-000000000002");
        UUID sessionId = UUID.fromString("00000000-0000-7000-8000-000000000003");
        UUID referenceId = UUID.fromString("00000000-0000-7000-8000-000000000005");
        List<CapturedRequest> requests = new ArrayList<>();
        HttpServer server = startCapabilityServer(requests, objectId, versionId, referenceId);
        try {
            RequestContextHolder.resetRequestAttributes();
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    () -> "service-token-1"
            );

            client.prepareUpload(uploadSessionRequest());
            client.completeProxyUpload(new OssCompleteUploadRequest(
                    sessionId,
                    objectId,
                    versionId,
                    () -> new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)),
                    "note.txt",
                    "text/plain",
                    2,
                    "sha256-note"
            ));
            client.getMetadata(objectId);
            client.createSignedDownloadUrl(objectId, 120);
            client.bindObjectReference(objectId, new OssBindReferenceRequest(
                    referenceId.toString(),
                    versionId.toString(),
                    "community-app",
                    "content",
                    "post-media",
                    "post-7",
                    "PRIMARY",
                    null,
                    "actor-7"
            ));
            client.getObjectReference(objectId, referenceId);
            client.releaseObjectReference(objectId, referenceId, "actor-7");
            client.deleteObject(objectId, "actor-7");

            assertThat(requests).extracting(CapturedRequest::method, CapturedRequest::path)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("POST", "/internal/oss/upload-sessions"),
                            org.assertj.core.groups.Tuple.tuple("POST", "/internal/oss/upload-sessions/" + sessionId + "/complete"),
                            org.assertj.core.groups.Tuple.tuple("GET", "/internal/oss/objects/" + objectId),
                            org.assertj.core.groups.Tuple.tuple("GET", "/internal/oss/objects/" + objectId + "/signed-url"),
                            org.assertj.core.groups.Tuple.tuple("POST", "/internal/oss/objects/" + objectId + "/references"),
                            org.assertj.core.groups.Tuple.tuple("GET", "/internal/oss/objects/" + objectId + "/references/" + referenceId),
                            org.assertj.core.groups.Tuple.tuple("DELETE", "/internal/oss/objects/" + objectId + "/references/" + referenceId),
                            org.assertj.core.groups.Tuple.tuple("DELETE", "/internal/oss/objects/" + objectId)
                    );
            assertThat(requests).allSatisfy(request ->
                    assertThat(request.authorization()).containsExactly("Bearer service-token-1"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void completeProxyUploadShouldPreserveOneShotMultipartContent() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID versionId = UUID.fromString("00000000-0000-7000-8000-000000000002");
        UUID sessionId = UUID.fromString("00000000-0000-7000-8000-000000000003");
        byte[] uploadContent = "avatar-data".getBytes(StandardCharsets.UTF_8);
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicInteger streamOpenCount = new AtomicInteger();
        AtomicInteger streamCloseCount = new AtomicInteger();
        AtomicReference<CapturedMultipartRequest> capturedRequest = new AtomicReference<>();
        HttpServer server = startCompleteUploadServer(capturedRequest, wrappedMetadataResponseWithOwnerFields());
        try {
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    () -> {
                        tokenCalls.incrementAndGet();
                        return "service-token-1";
                    }
            );

            OssMetadataResponse response = client.completeProxyUpload(new OssCompleteUploadRequest(
                    sessionId,
                    objectId,
                    versionId,
                    () -> {
                        streamOpenCount.incrementAndGet();
                        return new ByteArrayInputStream(uploadContent) {
                            @Override
                            public void close() throws IOException {
                                streamCloseCount.incrementAndGet();
                                super.close();
                            }
                        };
                    },
                    "avatar.png",
                    "image/png",
                    uploadContent.length,
                    "sha256-avatar"
            ));

            CapturedMultipartRequest request = capturedRequest.get();
            CapturedMultipartPart part = parseSingleMultipartPart(request.contentType(), request.body());
            assertThat(response.objectId()).isEqualTo(objectId);
            assertThat(tokenCalls).hasValue(1);
            assertThat(streamOpenCount).hasValue(1);
            assertThat(streamCloseCount).hasValue(1);
            assertThat(request.authorization()).containsExactly("Bearer service-token-1");
            assertThat(part.headers())
                    .contains("Content-Disposition: form-data; name=\"file\"; filename=\"avatar.png\"")
                    .contains("Content-Type: image/png")
                    .contains("Content-Length: " + uploadContent.length);
            assertThat(part.content()).containsExactly(uploadContent);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rotatedServiceTokenShouldApplyToTheNextRequest() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID versionId = UUID.fromString("00000000-0000-7000-8000-000000000002");
        UUID referenceId = UUID.fromString("00000000-0000-7000-8000-000000000005");
        List<CapturedRequest> requests = new ArrayList<>();
        AtomicReference<String> token = new AtomicReference<>("service-token-1");
        HttpServer server = startCapabilityServer(requests, objectId, versionId, referenceId);
        try {
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    token::get
            );

            client.getMetadata(objectId);
            token.set("service-token-2");
            client.getMetadata(objectId);

            assertThat(requests).extracting(CapturedRequest::authorization)
                    .containsExactly(
                            List.of("Bearer service-token-1"),
                            List.of("Bearer service-token-2")
                    );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void prefixedInternalBaseUrlShouldOverrideCallerInterceptorWithFreshServiceToken() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID versionId = UUID.fromString("00000000-0000-7000-8000-000000000002");
        UUID referenceId = UUID.fromString("00000000-0000-7000-8000-000000000005");
        List<CapturedRequest> requests = new ArrayList<>();
        AtomicInteger tokenCalls = new AtomicInteger();
        HttpServer server = startCapabilityServer(requests, objectId, versionId, referenceId);
        try {
            RestClient.Builder callerBuilder = RestClient.builder()
                    .requestInterceptor((request, body, execution) -> {
                        request.getHeaders().set(
                                HttpHeaders.AUTHORIZATION, "Bearer leaked-interceptor");
                        return execution.execute(request, body);
                    });
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/proxy",
                    callerBuilder,
                    () -> "service-token-" + tokenCalls.incrementAndGet()
            );

            client.prepareUpload(uploadSessionRequest());
            client.prepareUpload(uploadSessionRequest());

            assertThat(requests).extracting(CapturedRequest::path)
                    .containsExactly(
                            "/proxy/internal/oss/upload-sessions",
                            "/proxy/internal/oss/upload-sessions"
                    );
            assertThat(requests).extracting(CapturedRequest::authorization)
                    .containsExactly(
                            List.of("Bearer service-token-1"),
                            List.of("Bearer service-token-2")
                    );
            assertThat(tokenCalls).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void completeProxyUploadShouldReachNetworkBeforeSourceEofWithoutCallerInterceptors() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID versionId = UUID.fromString("00000000-0000-7000-8000-000000000002");
        UUID sessionId = UUID.fromString("00000000-0000-7000-8000-000000000003");
        byte[] uploadContent = new byte[32 * 1024 + 16];
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicInteger callerInterceptorCalls = new AtomicInteger();
        AtomicInteger streamOpenCount = new AtomicInteger();
        AtomicInteger streamCloseCount = new AtomicInteger();
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger tokenOrder = new AtomicInteger();
        AtomicInteger streamOpenOrder = new AtomicInteger();
        AtomicReference<List<String>> authorization = new AtomicReference<>();
        CountDownLatch requestBodyObserved = new CountDownLatch(1);
        HttpServer server = startGatedCompleteUploadServer(
                authorization, requestBodyObserved, wrappedMetadataResponseWithOwnerFields());
        try {
            RestClient.Builder callerBuilder = RestClient.builder()
                    .requestInterceptor((request, body, execution) -> {
                        callerInterceptorCalls.incrementAndGet();
                        request.getHeaders().set(
                                HttpHeaders.AUTHORIZATION, "Bearer leaked-interceptor");
                        return execution.execute(request, body);
                    });
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    callerBuilder,
                    () -> {
                        tokenCalls.incrementAndGet();
                        tokenOrder.set(sequence.incrementAndGet());
                        return "service-token-1";
                    }
            );

            OssMetadataResponse response = client.completeProxyUpload(new OssCompleteUploadRequest(
                    sessionId,
                    objectId,
                    versionId,
                    () -> {
                        streamOpenCount.incrementAndGet();
                        streamOpenOrder.set(sequence.incrementAndGet());
                        return new GatedInputStream(uploadContent, requestBodyObserved, streamCloseCount);
                    },
                    "avatar.png",
                    "image/png",
                    uploadContent.length,
                    "sha256-avatar"
            ));

            assertThat(response.objectId()).isEqualTo(objectId);
            assertThat(authorization.get()).containsExactly("Bearer service-token-1");
            assertThat(tokenCalls).hasValue(1);
            assertThat(callerInterceptorCalls).hasValue(0);
            assertThat(streamOpenCount).hasValue(1);
            assertThat(streamCloseCount).hasValue(1);
            assertThat(tokenOrder.get()).isPositive().isLessThan(streamOpenOrder.get());
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
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(), () -> "service-token-1");

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
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(), () -> "service-token-1");

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
            HttpCommunityOssClient client = new HttpCommunityOssClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(), () -> "service-token-1");

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
                    "http://127.0.0.1:" + server.getAddress().getPort(), () -> "service-token-1");

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
        server.createContext("/internal/oss/upload-sessions", exchange -> {
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
        server.createContext("/internal/oss/objects/" + objectId, exchange -> {
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

    private static HttpServer startPublicFileServer(
            AtomicReference<String> authorization,
            CountDownLatch requestReceived
    ) throws IOException {
        return startPublicFileServer(authorization, requestReceived, "/files/public.txt");
    }

    private static HttpServer startPublicFileServer(
            AtomicReference<String> authorization,
            CountDownLatch requestReceived,
            String contextPath
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(contextPath, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            requestReceived.countDown();
        });
        server.start();
        return server;
    }

    private static HttpServer startGatedCompleteUploadServer(
            AtomicReference<List<String>> authorization,
            CountDownLatch requestBodyObserved,
            String responseJson
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/oss/upload-sessions/", exchange -> {
            authorization.set(List.copyOf(
                    exchange.getRequestHeaders().getOrDefault(HttpHeaders.AUTHORIZATION, List.of())));
            InputStream requestBody = exchange.getRequestBody();
            if (requestBody.read() >= 0) {
                requestBodyObserved.countDown();
            }
            requestBody.readAllBytes();
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static HttpServer startCompleteUploadServer(
            AtomicReference<CapturedMultipartRequest> capturedRequest,
            String responseJson
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/oss/upload-sessions/", exchange -> {
            capturedRequest.set(new CapturedMultipartRequest(
                    List.copyOf(exchange.getRequestHeaders().getOrDefault(HttpHeaders.AUTHORIZATION, List.of())),
                    exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                    exchange.getRequestBody().readAllBytes()
            ));
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static HttpServer startCapabilityServer(
            List<CapturedRequest> requests,
            UUID objectId,
            UUID versionId,
            UUID referenceId
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(new CapturedRequest(
                    exchange.getRequestMethod(),
                    path,
                    List.copyOf(exchange.getRequestHeaders().getOrDefault(HttpHeaders.AUTHORIZATION, List.of()))
            ));
            exchange.getRequestBody().readAllBytes();
            String response = capabilityResponse(path, exchange.getRequestMethod(), objectId, versionId, referenceId);
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String capabilityResponse(
            String path,
            String method,
            UUID objectId,
            UUID versionId,
            UUID referenceId
    ) {
        if (path.endsWith("/upload-sessions")) {
            return wrappedUploadSessionResponse();
        }
        if (path.endsWith("/signed-url")) {
            return wrappedSignedUrlResponse();
        }
        if (path.contains("/references")) {
            return wrappedReferenceResponse(objectId, versionId, referenceId);
        }
        if ("DELETE".equals(method)) {
            return wrappedLifecycleResponse(objectId, versionId);
        }
        return wrappedMetadataResponseWithOwnerFields();
    }

    private static OssUploadSessionRequest uploadSessionRequest() {
        return new OssUploadSessionRequest(
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
        );
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

    private static String wrappedSignedUrlResponse() {
        return """
                {
                  "code": 0,
                  "message": "OK",
                  "httpStatus": 200,
                  "data": {
                    "url": "https://signed.example.test/object",
                    "method": "GET",
                    "expiresAt": "2026-05-07T00:05:00Z",
                    "cacheControl": "private, max-age=120"
                  }
                }
                """;
    }

    private static String wrappedLifecycleResponse(UUID objectId, UUID versionId) {
        return """
                {
                  "code": 0,
                  "message": "OK",
                  "httpStatus": 200,
                  "data": {
                    "objectId": "%s",
                    "currentVersionId": "%s",
                    "status": "PURGED",
                    "deletePending": false,
                    "purged": true,
                    "message": "object purged",
                    "updatedAt": "2026-05-07T00:10:00Z"
                  }
                }
                """.formatted(objectId, versionId);
    }

    private static CapturedMultipartPart parseSingleMultipartPart(String contentType, byte[] body) {
        String boundary = MediaType.parseMediaType(contentType).getParameter("boundary");
        if (boundary == null || boundary.isBlank()) {
            throw new IllegalArgumentException("multipart boundary is missing");
        }
        Charset charset = StandardCharsets.ISO_8859_1;
        byte[] openingBoundary = ("--" + boundary + "\r\n").getBytes(charset);
        byte[] headerSeparator = "\r\n\r\n".getBytes(charset);
        byte[] closingBoundary = ("\r\n--" + boundary + "--").getBytes(charset);
        int headersStart = requireSequence(body, openingBoundary, 0) + openingBoundary.length;
        int headersEnd = requireSequence(body, headerSeparator, headersStart);
        int contentStart = headersEnd + headerSeparator.length;
        int contentEnd = requireSequence(body, closingBoundary, contentStart);
        return new CapturedMultipartPart(
                new String(body, headersStart, headersEnd - headersStart, charset),
                Arrays.copyOfRange(body, contentStart, contentEnd)
        );
    }

    private static int requireSequence(byte[] source, byte[] sequence, int fromIndex) {
        outer:
        for (int index = fromIndex; index <= source.length - sequence.length; index++) {
            for (int offset = 0; offset < sequence.length; offset++) {
                if (source[index + offset] != sequence[offset]) {
                    continue outer;
                }
            }
            return index;
        }
        throw new IllegalArgumentException("multipart sequence is missing");
    }

    private record CapturedRequest(String method, String path, List<String> authorization) {
    }

    private record CapturedMultipartRequest(List<String> authorization, String contentType, byte[] body) {
    }

    private record CapturedMultipartPart(String headers, byte[] content) {
    }

    private static final class GatedInputStream extends InputStream {

        private static final int INITIAL_CHUNK_SIZE = 32 * 1024;

        private final byte[] content;
        private final CountDownLatch requestBodyObserved;
        private final AtomicInteger closeCount;
        private int offset;
        private boolean gatePassed;

        private GatedInputStream(
                byte[] content,
                CountDownLatch requestBodyObserved,
                AtomicInteger closeCount
        ) {
            this.content = content;
            this.requestBodyObserved = requestBodyObserved;
            this.closeCount = closeCount;
        }

        @Override
        public int read() throws IOException {
            awaitNetworkAfterInitialChunk();
            if (offset >= content.length) {
                return -1;
            }
            return content[offset++] & 0xff;
        }

        @Override
        public int read(byte[] destination, int destinationOffset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            awaitNetworkAfterInitialChunk();
            if (offset >= content.length) {
                return -1;
            }
            int bytesUntilGate = gatePassed ? content.length - offset : INITIAL_CHUNK_SIZE - offset;
            int copied = Math.min(length, Math.min(bytesUntilGate, content.length - offset));
            System.arraycopy(content, offset, destination, destinationOffset, copied);
            offset += copied;
            return copied;
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }

        private void awaitNetworkAfterInitialChunk() throws IOException {
            if (gatePassed || offset < Math.min(INITIAL_CHUNK_SIZE, content.length)) {
                return;
            }
            gatePassed = true;
            try {
                if (!requestBodyObserved.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("request body was buffered before network I/O");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for request body observation", e);
            }
        }
    }
}
