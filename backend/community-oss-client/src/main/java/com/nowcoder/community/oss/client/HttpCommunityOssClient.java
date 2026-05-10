package com.nowcoder.community.oss.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssAccessDecisionResponse;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssGrantObjectAccessRequest;
import com.nowcoder.community.oss.client.model.OssLifecycleResponse;
import com.nowcoder.community.oss.client.model.OssSignedUrlResponse;
import com.nowcoder.community.oss.client.model.OssPublicFileResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.core.io.InputStreamResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class HttpCommunityOssClient implements CommunityOssClient {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private final RestClient restClient;

    public HttpCommunityOssClient(String baseUrl) {
        this(baseUrl, null);
    }

    public HttpCommunityOssClient(String baseUrl, Supplier<String> fallbackBearerAuthorizationSupplier) {
        this(RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "http://community-oss:18090" : baseUrl.trim())
                .requestInterceptor((request, body, execution) -> {
                    String authorization = currentBearerAuthorization();
                    if (authorization.isBlank() && fallbackBearerAuthorizationSupplier != null) {
                        authorization = normalizeBearerAuthorization(fallbackBearerAuthorizationSupplier.get());
                    }
                    if (!authorization.isBlank() && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                        request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                    return execution.execute(request, body);
                })
                .build());
    }

    public HttpCommunityOssClient(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
    }

    @Override
    public OssUploadSessionResponse prepareUpload(OssUploadSessionRequest request) {
        return readBody(restClient.post()
                .uri("/api/oss/objects/upload-sessions")
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
        return readBody(restClient.post()
                .uri(builder -> builder
                        .path("/api/oss/objects/{objectId}/complete")
                        .queryParam("sessionId", request.sessionId())
                        .queryParam("versionId", request.versionId())
                        .queryParam("checksumSha256", request.checksumSha256())
                        .build(request.objectId()))
                .body(body)
                .retrieve()
                .body(String.class), OssMetadataResponse.class);
    }

    @Override
    public OssMetadataResponse getMetadata(UUID objectId) {
        return readBody(restClient.get()
                .uri("/api/oss/objects/{objectId}", objectId)
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
            throw e;
        }
    }

    @Override
    public OssSignedUrlResponse createSignedDownloadUrl(UUID objectId, long ttlSeconds) {
        return readBody(restClient.get()
                .uri(builder -> builder
                        .path("/api/oss/objects/{objectId}/signed-url")
                        .queryParam("ttlSeconds", ttlSeconds)
                        .build(objectId))
                .retrieve()
                .body(String.class), OssSignedUrlResponse.class);
    }

    @Override
    public OssAccessDecisionResponse grantObjectAccess(UUID objectId, OssGrantObjectAccessRequest request) {
        return readBody(restClient.post()
                .uri("/api/oss/objects/{objectId}/grants", objectId)
                .body(request)
                .retrieve()
                .body(String.class), OssAccessDecisionResponse.class);
    }

    @Override
    public OssAccessDecisionResponse revokeObjectAccess(UUID objectId, UUID grantId, String actorId) {
        return readBody(restClient.delete()
                .uri(builder -> builder
                        .path("/api/oss/objects/{objectId}/grants/{grantId}")
                        .queryParam("actorId", actorId == null ? "" : actorId)
                        .build(objectId, grantId))
                .retrieve()
                .body(String.class), OssAccessDecisionResponse.class);
    }

    @Override
    public OssReferenceResponse bindObjectReference(UUID objectId, OssBindReferenceRequest request) {
        return readBody(restClient.post()
                .uri("/internal/oss/objects/{objectId}/references", objectId)
                .body(request)
                .retrieve()
                .body(String.class), OssReferenceResponse.class);
    }

    @Override
    public OssReferenceResponse releaseObjectReference(UUID objectId, UUID referenceId, String actorId) {
        return readBody(restClient.delete()
                .uri(builder -> builder
                        .path("/internal/oss/objects/{objectId}/references/{referenceId}")
                        .queryParam("actorId", actorId == null ? "" : actorId)
                        .build(objectId, referenceId))
                .retrieve()
                .body(String.class), OssReferenceResponse.class);
    }

    @Override
    public OssLifecycleResponse deleteObject(UUID objectId, String actorId) {
        return readBody(restClient.delete()
                .uri(builder -> builder
                        .path("/api/oss/objects/{objectId}")
                        .queryParam("actorId", actorId == null ? "" : actorId)
                        .build(objectId))
                .retrieve()
                .body(String.class), OssLifecycleResponse.class);
    }

    private static <T> T readBody(String responseBody, Class<T> responseType) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode payload = root.has("code") && root.has("data") ? root.get("data") : root;
            if (payload == null || payload.isNull()) {
                return null;
            }
            return OBJECT_MAPPER.treeToValue(payload, responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse OSS response", e);
        }
    }

    private static String currentBearerAuthorization() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return "";
        }
        String authorization = servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return "";
        }
        return authorization;
    }

    private static String normalizeBearerAuthorization(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return "";
        }
        return authorization;
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
