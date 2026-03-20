package com.nowcoder.community.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare R2 (S3 compatible) config.
 *
 * <p>Used when {@code user.avatar.storage=r2}.</p>
 */
@ConfigurationProperties(prefix = "r2")
public class R2Properties {

    /**
     * S3 endpoint, e.g. https://&lt;accountid&gt;.r2.cloudflarestorage.com
     */
    private String endpoint;

    private String accessKey;
    private String secretKey;

    /**
     * Bucket name used for avatar objects.
     */
    private String bucketName;

    /**
     * R2 uses a virtual region value, commonly "auto".
     */
    private String region = "auto";

    /**
     * Prefer path-style access for S3-compatible endpoints unless you explicitly want virtual-host style.
     */
    private boolean pathStyle = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isPathStyle() {
        return pathStyle;
    }

    public void setPathStyle(boolean pathStyle) {
        this.pathStyle = pathStyle;
    }
}

