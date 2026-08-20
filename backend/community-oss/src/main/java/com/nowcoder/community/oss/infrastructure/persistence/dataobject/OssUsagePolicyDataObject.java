package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssUsagePolicy;
import com.nowcoder.community.oss.domain.model.OssVisibility;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record OssUsagePolicyDataObject(
        String usage,
        String defaultVisibility,
        long maxBytes,
        String allowedMimeTypes,
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

    public static OssUsagePolicyDataObject from(OssUsagePolicy policy) {
        return new OssUsagePolicyDataObject(
                policy.usage(), policy.defaultVisibility().name(), policy.maxBytes(),
                String.join(",", policy.allowedMimeTypes()), policy.requiresChecksum(), policy.requiresScan(),
                policy.versioningEnabled(), policy.downloadTtlSeconds(), policy.uploadTtlSeconds(),
                policy.publicCacheControl(), policy.privateCacheControl(), policy.retentionDays(), policy.deleteGraceDays()
        );
    }

    public OssUsagePolicy toDomain() {
        return new OssUsagePolicy(
                usage, OssVisibility.valueOf(defaultVisibility), maxBytes, parseMimeTypes(allowedMimeTypes),
                requiresChecksum, requiresScan, versioningEnabled, downloadTtlSeconds, uploadTtlSeconds,
                publicCacheControl, privateCacheControl, retentionDays, deleteGraceDays
        );
    }

    private Set<String> parseMimeTypes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
