package com.nowcoder.community.common.spring.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "community.upload")
public class UploadPolicyProperties {

    private DataSize maxFileSize = DataSize.ofGigabytes(10);
    private DataSize maxRequestSize = DataSize.ofGigabytes(10);
    private List<String> allowedMimeTypes = new ArrayList<>();
    private List<String> allowedExtensions = new ArrayList<>();
    private boolean avatarUploadEnabled = true;
    private boolean mediaUploadEnabled = true;

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize == null || maxFileSize.isNegative() ? DataSize.ofGigabytes(10) : maxFileSize;
    }

    public DataSize getMaxRequestSize() {
        return maxRequestSize;
    }

    public void setMaxRequestSize(DataSize maxRequestSize) {
        this.maxRequestSize = maxRequestSize == null || maxRequestSize.isNegative() ? DataSize.ofGigabytes(10) : maxRequestSize;
    }

    public List<String> getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    public void setAllowedMimeTypes(List<String> allowedMimeTypes) {
        this.allowedMimeTypes = normalizeList(allowedMimeTypes);
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = normalizeList(allowedExtensions);
    }

    public boolean isAvatarUploadEnabled() {
        return avatarUploadEnabled;
    }

    public void setAvatarUploadEnabled(boolean avatarUploadEnabled) {
        this.avatarUploadEnabled = avatarUploadEnabled;
    }

    public boolean isMediaUploadEnabled() {
        return mediaUploadEnabled;
    }

    public void setMediaUploadEnabled(boolean mediaUploadEnabled) {
        this.mediaUploadEnabled = mediaUploadEnabled;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }
        return normalized;
    }
}
