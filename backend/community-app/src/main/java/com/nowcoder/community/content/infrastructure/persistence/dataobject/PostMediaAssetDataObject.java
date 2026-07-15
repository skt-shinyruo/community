package com.nowcoder.community.content.infrastructure.persistence.dataobject;

import java.util.Date;
import java.util.UUID;

public class PostMediaAssetDataObject {

    private UUID id;
    private UUID ownerUserId;
    private UUID postId;
    private UUID ossObjectId;
    private UUID ossVersionId;
    private UUID ossReferenceId;
    private UUID uploadSessionId;
    private String fileName;
    private String contentType;
    private long contentLength;
    private String mediaKind;
    private String lifecycle;
    private String uploadStatus;
    private long uploadOperationVersion;
    private Date uploadUpdatedAt;
    private String referenceStatus;
    private long referenceOperationVersion;
    private Date referenceUpdatedAt;
    private String videoState;
    private String publicUrl;
    private String failureReason;
    private Date createTime;
    private Date updateTime;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public UUID getOssObjectId() {
        return ossObjectId;
    }

    public void setOssObjectId(UUID ossObjectId) {
        this.ossObjectId = ossObjectId;
    }

    public UUID getOssVersionId() {
        return ossVersionId;
    }

    public void setOssVersionId(UUID ossVersionId) {
        this.ossVersionId = ossVersionId;
    }

    public UUID getOssReferenceId() {
        return ossReferenceId;
    }

    public void setOssReferenceId(UUID ossReferenceId) {
        this.ossReferenceId = ossReferenceId;
    }

    public UUID getUploadSessionId() {
        return uploadSessionId;
    }

    public void setUploadSessionId(UUID uploadSessionId) {
        this.uploadSessionId = uploadSessionId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public String getMediaKind() {
        return mediaKind;
    }

    public void setMediaKind(String mediaKind) {
        this.mediaKind = mediaKind;
    }

    public String getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(String lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getUploadStatus() {
        if (uploadStatus != null) {
            return uploadStatus;
        }
        return "DRAFT".equals(lifecycle) ? "PREPARED" : "COMPLETED";
    }

    public void setUploadStatus(String uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public long getUploadOperationVersion() {
        return uploadOperationVersion;
    }

    public void setUploadOperationVersion(long uploadOperationVersion) {
        this.uploadOperationVersion = uploadOperationVersion;
    }

    public Date getUploadUpdatedAt() {
        if (uploadUpdatedAt != null) {
            return uploadUpdatedAt;
        }
        return updateTime == null ? createTime : updateTime;
    }

    public void setUploadUpdatedAt(Date uploadUpdatedAt) {
        this.uploadUpdatedAt = uploadUpdatedAt;
    }

    public String getReferenceStatus() {
        return referenceStatus == null ? "UNBOUND" : referenceStatus;
    }

    public void setReferenceStatus(String referenceStatus) {
        this.referenceStatus = referenceStatus;
    }

    public long getReferenceOperationVersion() {
        return referenceOperationVersion;
    }

    public void setReferenceOperationVersion(long referenceOperationVersion) {
        this.referenceOperationVersion = referenceOperationVersion;
    }

    public Date getReferenceUpdatedAt() {
        if (referenceUpdatedAt != null) {
            return referenceUpdatedAt;
        }
        return updateTime == null ? createTime : updateTime;
    }

    public void setReferenceUpdatedAt(Date referenceUpdatedAt) {
        this.referenceUpdatedAt = referenceUpdatedAt;
    }

    public String getVideoState() {
        return videoState;
    }

    public void setVideoState(String videoState) {
        this.videoState = videoState;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
