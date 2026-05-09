package com.nowcoder.community.drive.infrastructure.oss;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssSignedUrlResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.springframework.stereotype.Component;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;

@Component
public class OssDriveObjectStorageAdapter implements DriveObjectStoragePort {

    private final CommunityOssClient ossClient;

    public OssDriveObjectStorageAdapter(CommunityOssClient ossClient) {
        this.ossClient = ossClient;
    }

    @Override
    public PreparedObject prepareUpload(PrepareObject command) {
        OssUploadSessionResponse response = ossClient.prepareUpload(new OssUploadSessionRequest(
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
                "",
                command.actorId()
        ));
        if (response == null || response.sessionId() == null || response.objectId() == null || response.versionId() == null) {
            throw new BusinessException(INTERNAL_ERROR, "签发网盘上传参数失败");
        }
        return new PreparedObject(response.sessionId(), response.objectId(), response.versionId(), response.expiresAt());
    }

    @Override
    public StoredObject completeUpload(CompleteObject command) {
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
        return new StoredObject(response.objectId(), response.currentVersionId(), response.publicUrl());
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
        ossClient.deleteObject(objectId, actorId == null ? "" : actorId);
    }
}
