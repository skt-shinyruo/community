package com.nowcoder.community.oss.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssLifecycleResponse;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssPublicFileResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssSignedUrlResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.oss.client.OssClientException.Category.BAD_RESPONSE;
import static com.nowcoder.community.oss.client.OssClientException.Category.CONFLICT;
import static com.nowcoder.community.oss.client.OssClientException.Category.NOT_FOUND;
import static com.nowcoder.community.oss.client.OssClientException.Category.TIMEOUT;
import static com.nowcoder.community.oss.client.OssClientException.Category.TRANSIENT;

public class HttpCommunityOssClient implements CommunityOssClient {

    private static final JsonCodec JSON = new JacksonJsonCodec(JsonMappers.standard());

    private final RestClient restClient;
    private final OssServiceTokenProvider serviceTokenProvider;

    public HttpCommunityOssClient(String baseUrl, OssServiceTokenProvider serviceTokenProvider) {
        this(baseUrl, RestClient.builder(), serviceTokenProvider);
    }

    public HttpCommunityOssClient(
            String baseUrl,
            RestClient.Builder restClientBuilder,
            OssServiceTokenProvider serviceTokenProvider
    ) {
        RestClient.Builder builder = (restClientBuilder == null ? RestClient.builder() : restClientBuilder.clone())
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "http://community-oss:18090" : baseUrl.trim());
        this.restClient = builder.build();
        this.serviceTokenProvider = Objects.requireNonNull(serviceTokenProvider, "serviceTokenProvider");
    }

    public HttpCommunityOssClient(RestClient restClient, OssServiceTokenProvider serviceTokenProvider) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.serviceTokenProvider = Objects.requireNonNull(serviceTokenProvider, "serviceTokenProvider");
    }

    @Override
    public OssUploadSessionResponse prepareUpload(OssUploadSessionRequest request) {
        return execute(() -> restClient.post()
                .uri("/internal/oss/upload-sessions")
                .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                .body(request)
                .retrieve()
                .body(String.class), OssUploadSessionResponse.class);
    }

    @Override
    public OssMetadataResponse completeProxyUpload(OssCompleteUploadRequest request) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        org.springframework.http.HttpHeaders partHeaders = new org.springframework.http.HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(request.contentType()));
        partHeaders.setContentDispositionFormData("file", request.fileName());
        body.add("file", new org.springframework.http.HttpEntity<>(new UploadInputStreamResource(request), partHeaders));
        return execute(() -> restClient.post()
                .uri(builder -> builder
                        .path("/internal/oss/upload-sessions/{sessionId}/complete")
                        .queryParam("objectId", request.objectId())
                        .queryParam("versionId", request.versionId())
                        .queryParam("checksumSha256", request.checksumSha256())
                        .build(request.sessionId()))
                .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                .body(body)
                .retrieve()
                .body(String.class), OssMetadataResponse.class);
    }

    @Override
    public OssMetadataResponse getMetadata(UUID objectId) {
        return execute(() -> restClient.get()
                .uri("/internal/oss/objects/{objectId}", objectId)
                .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                .retrieve()
                .body(String.class), OssMetadataResponse.class);
    }

    @Override
    public OssPublicFileResponse loadPublicFile(String fileKey) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri("/files/" + fileKey)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] content = response.getBody() == null ? new byte[0] : response.getBody();
            MediaType contentType = response.getHeaders().getContentType();
            long contentLength = response.getHeaders().getContentLength();
            return new OssPublicFileResponse(
                    content,
                    contentType == null ? "application/octet-stream" : contentType.toString(),
                    contentLength >= 0 ? contentLength : content.length,
                    response.getHeaders().getETag(),
                    response.getHeaders().getCacheControl(),
                    response.getHeaders().getContentDisposition() == null ? "" : response.getHeaders().getContentDisposition().getFilename()
            );
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw fromHttpFailure(e);
        } catch (ResourceAccessException e) {
            throw fromResourceFailure(e);
        } catch (RestClientException e) {
            throw new OssClientException(TRANSIENT, 0, true, "OSS request failed", e);
        }
    }

    @Override
    public OssSignedUrlResponse createSignedDownloadUrl(UUID objectId, long ttlSeconds) {
        return execute(() -> restClient.get()
                .uri(builder -> builder
                        .path("/internal/oss/objects/{objectId}/signed-url")
                        .queryParam("ttlSeconds", ttlSeconds)
                        .build(objectId))
                .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                .retrieve()
                .body(String.class), OssSignedUrlResponse.class);
    }

    @Override
    public OssReferenceResponse bindObjectReference(UUID objectId, OssBindReferenceRequest request) {
        return execute(() -> restClient.post()
                .uri("/internal/oss/objects/{objectId}/references", objectId)
                .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                .body(request)
                .retrieve()
                .body(String.class), OssReferenceResponse.class);
    }

    @Override
    public OssReferenceResponse getObjectReference(UUID objectId, UUID referenceId) {
        return execute(() -> restClient.get()
                .uri("/internal/oss/objects/{objectId}/references/{referenceId}", objectId, referenceId)
                .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                .retrieve()
                .body(String.class), OssReferenceResponse.class);
    }

    @Override
    public OssReferenceResponse releaseObjectReference(UUID objectId, UUID referenceId, String actorId) {
        return execute(() -> restClient.delete()
                .uri(builder -> builder
                        .path("/internal/oss/objects/{objectId}/references/{referenceId}")
                        .queryParam("actorId", actorId == null ? "" : actorId)
                        .build(objectId, referenceId))
                .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                .retrieve()
                .body(String.class), OssReferenceResponse.class);
    }

    @Override
    public OssLifecycleResponse deleteObject(UUID objectId, String actorId) {
        return execute(() -> restClient.delete()
                .uri(builder -> builder
                        .path("/internal/oss/objects/{objectId}")
                        .queryParam("actorId", actorId == null ? "" : actorId)
                        .build(objectId))
                .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                .retrieve()
                .body(String.class), OssLifecycleResponse.class);
    }

    private static <T> T execute(Supplier<String> request, Class<T> responseType) {
        try {
            return readBody(request.get(), responseType, 200);
        } catch (OssClientException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw fromHttpFailure(e);
        } catch (ResourceAccessException e) {
            throw fromResourceFailure(e);
        } catch (RestClientException e) {
            throw new OssClientException(TRANSIENT, 0, true, "OSS request failed", e);
        }
    }

    private static <T> T readBody(String responseBody, Class<T> responseType, int successStatus) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new OssClientException(BAD_RESPONSE, successStatus, false, "OSS response body is empty");
        }
        try {
            JsonNode root = JSON.readTree(responseBody);
            if (!root.has("code") || !root.has("data")) {
                throw new OssClientException(
                        BAD_RESPONSE, successStatus, false, "OSS response is not a Result envelope");
            }
            if (root.get("code").asInt(-1) != 0) {
                String message = root.has("message") ? root.get("message").asText("OSS request failed") : "OSS request failed";
                int httpStatus = root.path("httpStatus").asInt(successStatus);
                throw forStatus(httpStatus, message, null);
            }
            JsonNode payload = root.get("data");
            if (payload == null || payload.isNull()) {
                return null;
            }
            return JSON.treeToValue(payload, responseType);
        } catch (JsonCodecException e) {
            throw new OssClientException(BAD_RESPONSE, successStatus, false, "failed to parse OSS response", e);
        }
    }

    private static OssClientException fromHttpFailure(RestClientResponseException failure) {
        int status = failure.getStatusCode().value();
        String message = failure.getStatusText();
        String responseBody = failure.getResponseBodyAsString();
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = JSON.readTree(responseBody);
                if (root.has("message")) {
                    message = root.get("message").asText(message);
                }
                status = root.path("httpStatus").asInt(status);
            } catch (JsonCodecException ignored) {
                // The HTTP status remains authoritative when an error body is malformed.
            }
        }
        return forStatus(status, message, failure);
    }

    private static OssClientException forStatus(int status, String message, Throwable cause) {
        if (status == 404) {
            return new OssClientException(NOT_FOUND, status, false, message, cause);
        }
        if (status == 409) {
            return new OssClientException(CONFLICT, status, false, message, cause);
        }
        if (status == 408 || status == 429 || status >= 500) {
            return new OssClientException(TRANSIENT, status, true, message, cause);
        }
        return new OssClientException(BAD_RESPONSE, status, false, message, cause);
    }

    private static OssClientException fromResourceFailure(ResourceAccessException failure) {
        if (hasTimeoutCause(failure)) {
            return new OssClientException(TIMEOUT, 0, true, "OSS request timed out", failure);
        }
        return new OssClientException(TRANSIENT, 0, true, "OSS request failed", failure);
    }

    private static boolean hasTimeoutCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String serviceAuthorization() {
        String token;
        try {
            token = serviceTokenProvider.tokenValue();
        } catch (RuntimeException ignored) {
            throw serviceAuthenticationUnavailable();
        }
        if (token == null || token.isBlank()) {
            throw serviceAuthenticationUnavailable();
        }
        return "Bearer " + token.trim();
    }

    private static OssClientException serviceAuthenticationUnavailable() {
        return new OssClientException(
                BAD_RESPONSE,
                0,
                false,
                "OSS service authentication unavailable"
        );
    }

    private static final class UploadInputStreamResource extends InputStreamResource {

        private final OssCompleteUploadRequest request;

        private UploadInputStreamResource(OssCompleteUploadRequest request) {
            super(open(request));
            this.request = request;
        }

        @Override
        public String getFilename() {
            return request.fileName();
        }

        @Override
        public long contentLength() {
            return request.contentLength();
        }

        private static InputStream open(OssCompleteUploadRequest request) {
            try {
                return request.openStream();
            } catch (IOException e) {
                throw new IllegalStateException("failed to open OSS upload content", e);
            }
        }
    }
}
