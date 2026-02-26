package com.nowcoder.community.user.api.dto;

public class AvatarUploadTokenResponse {

    /**
     * 存储策略：local/qiniu（面向前端的可用性提示）。
     */
    private String provider;

    private String uploadToken;
    private String fileName;
    private String bucketUrl;

    /**
     * local provider：服务端接收 multipart 的上传地址（通常为 /api/users/{id}/avatar/upload）。
     */
    private String uploadUrl;

    /**
     * local provider：上传方法（默认 POST）。
     */
    private String uploadMethod;

    /**
     * 上传大小限制（字节）。
     */
    private long maxBytes;

    /**
     * 允许的 MIME 列表（以 ; 分隔）。
     */
    private String mimeLimit;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getUploadToken() {
        return uploadToken;
    }

    public void setUploadToken(String uploadToken) {
        this.uploadToken = uploadToken;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getBucketUrl() {
        return bucketUrl;
    }

    public void setBucketUrl(String bucketUrl) {
        this.bucketUrl = bucketUrl;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getUploadMethod() {
        return uploadMethod;
    }

    public void setUploadMethod(String uploadMethod) {
        this.uploadMethod = uploadMethod;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public String getMimeLimit() {
        return mimeLimit;
    }

    public void setMimeLimit(String mimeLimit) {
        this.mimeLimit = mimeLimit;
    }
}
