package com.nowcoder.community.drive.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PrepareDriveUploadRequest {

    private String parentId;

    @NotBlank
    private String fileName;

    private String contentType;

    @NotNull
    @Min(0)
    private Long contentLength;

    private String checksumSha256;

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
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

    public Long getContentLength() {
        return contentLength;
    }

    public void setContentLength(Long contentLength) {
        this.contentLength = contentLength;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }
}
