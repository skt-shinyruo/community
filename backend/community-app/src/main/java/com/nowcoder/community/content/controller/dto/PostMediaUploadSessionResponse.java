package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PostMediaUploadSessionResponse {

    private UUID assetId;
    private String uploadId;
    private UploadInstruction upload;
    private Constraints constraints;
    private String expiresAt;

    public static PostMediaUploadSessionResponse from(PostMediaUploadSessionResult result) {
        if (result == null) {
            return null;
        }
        PostMediaUploadSessionResponse response = new PostMediaUploadSessionResponse();
        response.setAssetId(result.assetId());
        response.setUploadId(result.uploadId());

        UploadInstruction upload = new UploadInstruction();
        upload.setUrl(result.uploadUrl());
        upload.setMethod(result.uploadMethod());
        upload.setFileField(result.fileField());
        upload.setFields(Map.of(result.uploadIdField(), result.uploadId()));
        upload.setHeaders(Map.of());
        response.setUpload(upload);

        Constraints constraints = new Constraints();
        constraints.setMaxBytes(result.maxBytes());
        constraints.setMimeTypes(parseMimeTypes(result.mimeTypes()));
        response.setConstraints(constraints);
        response.setExpiresAt(result.expiresAt() == null ? "" : result.expiresAt().toString());
        return response;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public UploadInstruction getUpload() {
        return upload;
    }

    public void setUpload(UploadInstruction upload) {
        this.upload = upload;
    }

    public Constraints getConstraints() {
        return constraints;
    }

    public void setConstraints(Constraints constraints) {
        this.constraints = constraints;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    private static List<String> parseMimeTypes(String mimeTypes) {
        if (mimeTypes == null || mimeTypes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(mimeTypes.split(";"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public static class UploadInstruction {
        private String url;
        private String method;
        private String fileField;
        private Map<String, String> fields = Map.of();
        private Map<String, String> headers = Map.of();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getFileField() {
            return fileField;
        }

        public void setFileField(String fileField) {
            this.fileField = fileField;
        }

        public Map<String, String> getFields() {
            return fields;
        }

        public void setFields(Map<String, String> fields) {
            this.fields = fields == null ? Map.of() : Map.copyOf(fields);
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }

    public static class Constraints {
        private long maxBytes;
        private List<String> mimeTypes = List.of();

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        public List<String> getMimeTypes() {
            return mimeTypes;
        }

        public void setMimeTypes(List<String> mimeTypes) {
            this.mimeTypes = mimeTypes == null ? List.of() : List.copyOf(mimeTypes);
        }
    }
}
