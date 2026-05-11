package com.nowcoder.community.user.controller.dto;

import java.util.List;
import java.util.Map;

public class AvatarUploadSessionResponse {

    private String uploadId;
    private String objectId;
    private String versionId;
    private UploadInstruction upload;
    private Constraints constraints;
    private String expiresAt;

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
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
