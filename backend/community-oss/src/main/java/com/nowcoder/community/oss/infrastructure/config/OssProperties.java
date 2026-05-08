package com.nowcoder.community.oss.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    private String publicBaseUrl = "http://localhost:12880";
    private ObjectStoreProperties objectStore = new ObjectStoreProperties();

    public String publicBaseUrl() {
        return publicBaseUrl;
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

        private String mode = "garage";
        private String endpoint = "http://garage:3900";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "community-oss";
        private boolean pathStyle = true;
        private String localRoot = "/tmp/community-oss";

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
    }
}
