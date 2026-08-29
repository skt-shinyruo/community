package com.nowcoder.community.drive.infrastructure.oss;

import com.nowcoder.community.oss.client.HttpCommunityOssClient;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssLifecycleResponse;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssSignedUrlResponse;
import com.nowcoder.community.oss.client.model.OssUploadCancellationResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.springframework.stereotype.Component;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;

@Component
public class OssDriveObjectStorageAdapter implements DriveObjectStoragePort {

    private final HttpCommunityOssClient ossClient;

    public OssDriveObjectStorageAdapter(HttpCommunityOssClient ossClient) {
        this.ossClient = ossClient;
    }

    @Override
    public PreparedObject prepareUpload(PrepareObject command) {
        OssUploadSessionResponse response = ossClient.prepareUpload(new OssUploadSessionRequest(
                command.requestId(),
                command.usage(),
                command.ownerService(),
                command.ownerDomain(),
                command.ownerType(),
                command.ownerId(),
                command.visibility(),
                command.fileName(),
                command.contentType(),
                command.contentLength(),
                command.checksumSha256(),
                command.actorId()
        ));
        if (response == null || response.sessionId() == null || response.objectId() == null || response.versionId() == null) {
            throw new BusinessException(INTERNAL_ERROR, "签发网盘上传参数失败");
        }
        return new PreparedObject(response.sessionId(), response.objectId(), response.versionId(), response.expiresAt());
    }

    @Override
    public void completeUpload(CompleteObject command) {
        OssMetadataResponse response = ossClient.completeProxyUpload(new OssCompleteUploadRequest(
                command.sessionId(),
                command.objectId(),
                command.versionId(),
                command.content()::openStream,
                command.fileName(),
                command.contentType(),
                command.contentLength(),
                command.checksumSha256()
        ));
        if (response == null || response.objectId() == null || response.currentVersionId() == null) {
            throw new BusinessException(INTERNAL_ERROR, "上传网盘文件失败");
        }
    }

    @Override
    public UploadCancellation cancelUpload(
            java.util.UUID sessionId,
            java.util.UUID objectId,
            java.util.UUID versionId
    ) {
        OssUploadCancellationResponse response = ossClient.cancelUpload(sessionId, objectId, versionId);
        if (response == null
                || !sessionId.equals(response.sessionId())
                || !objectId.equals(response.objectId())
                || !versionId.equals(response.versionId())) {
            throw new BusinessException(INTERNAL_ERROR, "取消网盘上传失败");
        }
        boolean completedStatus = "COMPLETED".equalsIgnoreCase(response.status());
        boolean cancelledStatus = "CANCELLED".equalsIgnoreCase(response.status());
        if (response.completed() == response.cancelled()
                || response.completed() != completedStatus
                || response.cancelled() != cancelledStatus) {
            throw new BusinessException(INTERNAL_ERROR, "取消网盘上传失败");
        }
        return new UploadCancellation(response.completed(), response.cancelled());
    }

    @Override
    public ObjectMetadata getMetadata(java.util.UUID objectId) {
        OssMetadataResponse response = ossClient.getMetadata(objectId);
        if (response == null || response.objectId() == null) {
            return null;
        }
        return new ObjectMetadata(
                response.objectId(),
                response.currentVersionId(),
                response.status()
        );
    }

    @Override
    public SignedDownloadUrl createDownloadUrl(java.util.UUID objectId, long ttlSeconds) {
        OssSignedUrlResponse response = ossClient.createSignedDownloadUrl(objectId, ttlSeconds);
        if (response == null || response.url() == null || response.url().isBlank()) {
            throw new BusinessException(INTERNAL_ERROR, "创建网盘下载链接失败");
        }
        return new SignedDownloadUrl(response.url(), response.expiresAt());
    }

    @Override
    public void deleteObject(java.util.UUID objectId, String actorId) {
        OssLifecycleResponse response = ossClient.deleteObject(objectId, actorId == null ? "" : actorId);
        if (response == null
                || !objectId.equals(response.objectId())
                || !"PURGED".equalsIgnoreCase(response.status())
                || !response.purged()
                || response.deletePending()) {
            throw new BusinessException(INTERNAL_ERROR, "删除网盘文件失败");
        }
    }
}
