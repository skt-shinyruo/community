package com.nowcoder.community.oss.infrastructure.config;

import com.nowcoder.community.oss.application.port.ObjectStorageSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oss")
public class OssProperties implements ObjectStorageSettings {

    private String publicBaseUrl = "http://localhost:12880";
    private ObjectStoreProperties objectStore = new ObjectStoreProperties();

    @Override
    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    @Override
    public String storageBucket() {
        return objectStore.bucket();
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public ObjectStoreProperties objectStore() {
        return objectStore;
    }

    public void setObjectStore(ObjectStoreProperties objectStore) {
        this.objectStore = objectStore;
    }

    public static class ObjectStoreProperties {

        private static final Duration DEFAULT_API_CALL_TIMEOUT = Duration.ofMinutes(30);
        private static final Duration MAX_API_CALL_TIMEOUT = Duration.ofMinutes(30);

        private String mode = "garage";
        private String endpoint = "http://garage:3900";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "community-oss";
        private String region = "garage";
        private boolean pathStyle = true;
        private String localRoot = "/tmp/community-oss";
        private Duration apiCallTimeout = DEFAULT_API_CALL_TIMEOUT;

        public String mode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String endpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String accessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String secretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String bucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String region() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public boolean pathStyle() {
            return pathStyle;
        }

        public void setPathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
        }

        public String localRoot() {
            return localRoot;
        }

        public void setLocalRoot(String localRoot) {
            this.localRoot = localRoot;
        }

        public Duration apiCallTimeout() {
            Duration configured = apiCallTimeout == null ? DEFAULT_API_CALL_TIMEOUT : apiCallTimeout;
            if (configured.isZero() || configured.isNegative()) {
                throw new IllegalStateException("oss.object-store.api-call-timeout must be positive");
            }
            if (configured.compareTo(MAX_API_CALL_TIMEOUT) > 0) {
                throw new IllegalStateException("oss.object-store.api-call-timeout must not exceed 30 minutes");
            }
            return configured;
        }

        public void setApiCallTimeout(Duration apiCallTimeout) {
            this.apiCallTimeout = apiCallTimeout;
        }
    }
}
