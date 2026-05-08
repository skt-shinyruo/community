package com.nowcoder.community.oss.client;

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
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

public class HttpCommunityOssClient implements CommunityOssClient {

    private final RestClient restClient;

    public HttpCommunityOssClient(String baseUrl) {
        this(RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "http://community-oss:18090" : baseUrl.trim())
                .build());
    }

    public HttpCommunityOssClient(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
    }

    @Override
    public OssUploadSessionResponse prepareUpload(OssUploadSessionRequest request) {
        return restClient.post()
                .uri("/api/oss/objects/upload-sessions")
                .body(request)
                .retrieve()
                .body(OssUploadSessionResponse.class);
    }

    @Override
    public OssMetadataResponse completeProxyUpload(OssCompleteUploadRequest request) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        org.springframework.http.HttpHeaders partHeaders = new org.springframework.http.HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(request.contentType()));
        partHeaders.setContentDispositionFormData("file", request.fileName());
        body.add("file", new org.springframework.http.HttpEntity<>(new UploadInputStreamResource(request), partHeaders));
        return restClient.post()
                .uri(builder -> builder
                        .path("/api/oss/objects/{objectId}/complete")
                        .queryParam("sessionId", request.sessionId())
                        .queryParam("versionId", request.versionId())
                        .queryParam("checksumSha256", request.checksumSha256())
                        .build(request.objectId()))
                .body(body)
                .retrieve()
                .body(OssMetadataResponse.class);
    }

    @Override
    public OssMetadataResponse getMetadata(UUID objectId) {
        return restClient.get()
                .uri("/api/oss/objects/{objectId}", objectId)
                .retrieve()
                .body(OssMetadataResponse.class);
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
        return restClient.get()
                .uri(builder -> builder
                        .path("/api/oss/objects/{objectId}/signed-url")
                        .queryParam("ttlSeconds", ttlSeconds)
                        .build(objectId))
                .retrieve()
                .body(OssSignedUrlResponse.class);
    }

    @Override
    public OssAccessDecisionResponse grantObjectAccess(UUID objectId, OssGrantObjectAccessRequest request) {
        return restClient.post()
                .uri("/api/oss/objects/{objectId}/grants", objectId)
                .body(request)
                .retrieve()
                .body(OssAccessDecisionResponse.class);
    }

    @Override
    public OssAccessDecisionResponse revokeObjectAccess(UUID objectId, UUID grantId, String actorId) {
        return restClient.delete()
                .uri(builder -> builder
                        .path("/api/oss/objects/{objectId}/grants/{grantId}")
                        .queryParam("actorId", actorId == null ? "" : actorId)
                        .build(objectId, grantId))
                .retrieve()
                .body(OssAccessDecisionResponse.class);
    }

    @Override
    public OssReferenceResponse bindObjectReference(UUID objectId, OssBindReferenceRequest request) {
        return restClient.post()
                .uri("/internal/oss/objects/{objectId}/references", objectId)
                .body(request)
                .retrieve()
                .body(OssReferenceResponse.class);
    }

    @Override
    public OssReferenceResponse releaseObjectReference(UUID objectId, UUID referenceId, String actorId) {
        return restClient.delete()
                .uri(builder -> builder
                        .path("/internal/oss/objects/{objectId}/references/{referenceId}")
                        .queryParam("actorId", actorId == null ? "" : actorId)
                        .build(objectId, referenceId))
                .retrieve()
                .body(OssReferenceResponse.class);
    }

    @Override
    public OssLifecycleResponse deleteObject(UUID objectId, String actorId) {
        return restClient.delete()
                .uri(builder -> builder
                        .path("/api/oss/objects/{objectId}")
                        .queryParam("actorId", actorId == null ? "" : actorId)
                        .build(objectId))
                .retrieve()
                .body(OssLifecycleResponse.class);
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
