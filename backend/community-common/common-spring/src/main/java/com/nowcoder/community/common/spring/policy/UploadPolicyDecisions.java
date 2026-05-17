package com.nowcoder.community.common.spring.policy;

import org.springframework.util.unit.DataSize;

import java.util.Locale;

public class UploadPolicyDecisions {

    private final UploadPolicyProperties properties;

    public UploadPolicyDecisions(UploadPolicyProperties properties) {
        this.properties = properties == null ? new UploadPolicyProperties() : properties;
    }

    public boolean allowsMimeType(String mimeType) {
        if (properties.getAllowedMimeTypes().isEmpty()) {
            return true;
        }
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        return properties.getAllowedMimeTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    public boolean allowsExtension(String extension) {
        if (properties.getAllowedExtensions().isEmpty()) {
            return true;
        }
        if (extension == null || extension.isBlank()) {
            return false;
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        String candidate = normalized;
        return properties.getAllowedExtensions().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .map(value -> value.startsWith(".") ? value.substring(1) : value)
                .anyMatch(candidate::equals);
    }

    public boolean allowsFileName(String fileName) {
        return allowsExtension(extensionOf(fileName));
    }

    public boolean allowsFileSize(long bytes) {
        return bytes >= 0 && bytes <= maxFileSizeBytes();
    }

    public long maxFileSizeBytes() {
        return bytes(properties.getMaxFileSize(), new UploadPolicyProperties().getMaxFileSize());
    }

    public long maxRequestSizeBytes() {
        return bytes(properties.getMaxRequestSize(), new UploadPolicyProperties().getMaxRequestSize());
    }

    public boolean avatarUploadEnabled() {
        return properties.isAvatarUploadEnabled();
    }

    public boolean mediaUploadEnabled() {
        return properties.isMediaUploadEnabled();
    }

    private String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String normalized = fileName.trim();
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dot + 1);
    }

    private long bytes(DataSize dataSize, DataSize fallback) {
        return dataSize == null ? fallback.toBytes() : dataSize.toBytes();
    }
}
