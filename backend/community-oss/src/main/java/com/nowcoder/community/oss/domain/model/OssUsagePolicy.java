package com.nowcoder.community.oss.domain.model;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record OssUsagePolicy(
        String usage,
        OssVisibility defaultVisibility,
        long maxBytes,
        Set<String> allowedMimeTypes,
        boolean requiresChecksum,
        boolean requiresScan,
        boolean versioningEnabled,
        long downloadTtlSeconds,
        long uploadTtlSeconds,
        String publicCacheControl,
        String privateCacheControl,
        int retentionDays,
        int deleteGraceDays
) {

    public OssUsagePolicy {
        usage = requireText(usage, "usage");
        defaultVisibility = defaultVisibility == null ? OssVisibility.INTERNAL : defaultVisibility;
        maxBytes = Math.max(1, maxBytes);
        allowedMimeTypes = normalizeMimeTypes(allowedMimeTypes);
        downloadTtlSeconds = Math.max(1, downloadTtlSeconds);
        uploadTtlSeconds = Math.max(1, uploadTtlSeconds);
        publicCacheControl = normalize(publicCacheControl);
        privateCacheControl = normalize(privateCacheControl);
        retentionDays = Math.max(0, retentionDays);
        deleteGraceDays = Math.max(0, deleteGraceDays);
    }

    public void validateUpload(String contentType, long contentLength, String checksumSha256) {
        if (contentLength > maxBytes) {
            throw new IllegalArgumentException("upload exceeds usage policy maxBytes");
        }
        String normalizedContentType = normalize(contentType).toLowerCase();
        if (!allowedMimeTypes.isEmpty() && !allowedMimeTypes.contains(normalizedContentType)) {
            throw new IllegalArgumentException("content type is not allowed for usage");
        }
        if (requiresChecksum && normalize(checksumSha256).isBlank()) {
            throw new IllegalArgumentException("checksum is required for usage");
        }
    }

    private static Set<String> normalizeMimeTypes(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            String candidate = normalize(value).toLowerCase();
            if (!candidate.isBlank()) {
                normalized.add(candidate);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim();
    }
}
