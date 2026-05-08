package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssUsagePolicy;
import com.nowcoder.community.oss.domain.model.OssVisibility;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class OssUsagePolicyDataObject {

    private String usage;
    private String defaultVisibility;
    private long maxBytes;
    private String allowedMimeTypes;
    private boolean requiresChecksum;
    private boolean requiresScan;
    private boolean versioningEnabled;
    private long downloadTtlSeconds;
    private long uploadTtlSeconds;
    private String publicCacheControl;
    private String privateCacheControl;
    private int retentionDays;
    private int deleteGraceDays;

    public static OssUsagePolicyDataObject from(OssUsagePolicy policy) {
        OssUsagePolicyDataObject row = new OssUsagePolicyDataObject();
        row.setUsage(policy.usage());
        row.setDefaultVisibility(policy.defaultVisibility().name());
        row.setMaxBytes(policy.maxBytes());
        row.setAllowedMimeTypes(String.join(",", policy.allowedMimeTypes()));
        row.setRequiresChecksum(policy.requiresChecksum());
        row.setRequiresScan(policy.requiresScan());
        row.setVersioningEnabled(policy.versioningEnabled());
        row.setDownloadTtlSeconds(policy.downloadTtlSeconds());
        row.setUploadTtlSeconds(policy.uploadTtlSeconds());
        row.setPublicCacheControl(policy.publicCacheControl());
        row.setPrivateCacheControl(policy.privateCacheControl());
        row.setRetentionDays(policy.retentionDays());
        row.setDeleteGraceDays(policy.deleteGraceDays());
        return row;
    }

    public OssUsagePolicy toDomain() {
        return new OssUsagePolicy(
                usage,
                OssVisibility.valueOf(defaultVisibility),
                maxBytes,
                parseMimeTypes(allowedMimeTypes),
                requiresChecksum,
                requiresScan,
                versioningEnabled,
                downloadTtlSeconds,
                uploadTtlSeconds,
                publicCacheControl,
                privateCacheControl,
                retentionDays,
                deleteGraceDays
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

    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }
    public String getDefaultVisibility() { return defaultVisibility; }
    public void setDefaultVisibility(String defaultVisibility) { this.defaultVisibility = defaultVisibility; }
    public long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
    public String getAllowedMimeTypes() { return allowedMimeTypes; }
    public void setAllowedMimeTypes(String allowedMimeTypes) { this.allowedMimeTypes = allowedMimeTypes; }
    public boolean isRequiresChecksum() { return requiresChecksum; }
    public void setRequiresChecksum(boolean requiresChecksum) { this.requiresChecksum = requiresChecksum; }
    public boolean isRequiresScan() { return requiresScan; }
    public void setRequiresScan(boolean requiresScan) { this.requiresScan = requiresScan; }
    public boolean isVersioningEnabled() { return versioningEnabled; }
    public void setVersioningEnabled(boolean versioningEnabled) { this.versioningEnabled = versioningEnabled; }
    public long getDownloadTtlSeconds() { return downloadTtlSeconds; }
    public void setDownloadTtlSeconds(long downloadTtlSeconds) { this.downloadTtlSeconds = downloadTtlSeconds; }
    public long getUploadTtlSeconds() { return uploadTtlSeconds; }
    public void setUploadTtlSeconds(long uploadTtlSeconds) { this.uploadTtlSeconds = uploadTtlSeconds; }
    public String getPublicCacheControl() { return publicCacheControl; }
    public void setPublicCacheControl(String publicCacheControl) { this.publicCacheControl = publicCacheControl; }
    public String getPrivateCacheControl() { return privateCacheControl; }
    public void setPrivateCacheControl(String privateCacheControl) { this.privateCacheControl = privateCacheControl; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    public int getDeleteGraceDays() { return deleteGraceDays; }
    public void setDeleteGraceDays(int deleteGraceDays) { this.deleteGraceDays = deleteGraceDays; }
}
