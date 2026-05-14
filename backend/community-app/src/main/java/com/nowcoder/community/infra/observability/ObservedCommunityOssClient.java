package com.nowcoder.community.infra.observability;

import com.nowcoder.community.common.observability.oss.OssRuntimeLogger;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssAccessDecisionResponse;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssGrantObjectAccessRequest;
import com.nowcoder.community.oss.client.model.OssLifecycleResponse;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssPublicFileResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssSignedUrlResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;
import java.util.function.Supplier;

public class ObservedCommunityOssClient implements CommunityOssClient {

    private static final String BUCKET_UNKNOWN = "-";

    private final CommunityOssClient delegate;
    private final OssRuntimeLogger logger;

    public ObservedCommunityOssClient(CommunityOssClient delegate, OssRuntimeLogger logger) {
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public OssUploadSessionResponse prepareUpload(OssUploadSessionRequest request) {
        long objectSize = request == null ? -1 : request.contentLength();
        return observeTransfer("upload", objectSize, () -> delegate.prepareUpload(request));
    }

    @Override
    public OssMetadataResponse completeProxyUpload(OssCompleteUploadRequest request) {
        long objectSize = request == null ? -1 : request.contentLength();
        return observeTransfer("upload", objectSize, () -> delegate.completeProxyUpload(request));
    }

    @Override
    public OssMetadataResponse getMetadata(UUID objectId) {
        return observeClientCall("metadata", () -> delegate.getMetadata(objectId));
    }

    @Override
    public OssPublicFileResponse loadPublicFile(String fileKey) {
        return observeTransfer("download", -1, () -> delegate.loadPublicFile(fileKey));
    }

    @Override
    public OssSignedUrlResponse createSignedDownloadUrl(UUID objectId, long ttlSeconds) {
        return observeTransfer("download", -1, () -> delegate.createSignedDownloadUrl(objectId, ttlSeconds));
    }

    @Override
    public OssAccessDecisionResponse grantObjectAccess(UUID objectId, OssGrantObjectAccessRequest request) {
        return observeClientCall("grant", () -> delegate.grantObjectAccess(objectId, request));
    }

    @Override
    public OssAccessDecisionResponse revokeObjectAccess(UUID objectId, UUID grantId, String actorId) {
        return observeClientCall("revoke", () -> delegate.revokeObjectAccess(objectId, grantId, actorId));
    }

    @Override
    public OssReferenceResponse bindObjectReference(UUID objectId, OssBindReferenceRequest request) {
        return observeClientCall("bind_reference", () -> delegate.bindObjectReference(objectId, request));
    }

    @Override
    public OssReferenceResponse releaseObjectReference(UUID objectId, UUID referenceId, String actorId) {
        return observeClientCall("release_reference", () -> delegate.releaseObjectReference(objectId, referenceId, actorId));
    }

    @Override
    public OssLifecycleResponse deleteObject(UUID objectId, String actorId) {
        return observeClientCall("delete", () -> delegate.deleteObject(objectId, actorId));
    }

    private <T> T observeTransfer(String operation, long objectSizeBytes, Supplier<T> supplier) {
        long startedAtNanos = System.nanoTime();
        try {
            T result = supplier.get();
            logger.logSlowOperation(operation, BUCKET_UNKNOWN, null, objectSizeBytes, elapsedMillis(startedAtNanos));
            return result;
        } catch (RuntimeException ex) {
            logger.logClientError(operation, BUCKET_UNKNOWN, null, errorCode(ex), ex);
            throw ex;
        }
    }

    private <T> T observeClientCall(String operation, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            logger.logClientError(operation, BUCKET_UNKNOWN, null, errorCode(ex), ex);
            throw ex;
        }
    }

    private String errorCode(RuntimeException ex) {
        if (ex instanceof RestClientResponseException responseException) {
            return Integer.toString(responseException.getStatusCode().value());
        }
        return ex.getClass().getSimpleName();
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
