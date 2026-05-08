package com.nowcoder.community.oss.client;

import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssAccessDecisionResponse;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssGrantObjectAccessRequest;
import com.nowcoder.community.oss.client.model.OssLifecycleResponse;
import com.nowcoder.community.oss.client.model.OssPublicFileResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssSignedUrlResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;

import java.util.UUID;

public interface CommunityOssClient {

    OssUploadSessionResponse prepareUpload(OssUploadSessionRequest request);

    OssMetadataResponse completeProxyUpload(OssCompleteUploadRequest request);

    OssMetadataResponse getMetadata(UUID objectId);

    OssPublicFileResponse loadPublicFile(String fileKey);

    OssSignedUrlResponse createSignedDownloadUrl(UUID objectId, long ttlSeconds);

    OssAccessDecisionResponse grantObjectAccess(UUID objectId, OssGrantObjectAccessRequest request);

    OssAccessDecisionResponse revokeObjectAccess(UUID objectId, UUID grantId, String actorId);

    OssReferenceResponse bindObjectReference(UUID objectId, OssBindReferenceRequest request);

    OssReferenceResponse releaseObjectReference(UUID objectId, UUID referenceId, String actorId);

    OssLifecycleResponse deleteObject(UUID objectId, String actorId);
}
