package com.nowcoder.community.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "frontend.runtime")
public class RuntimeConfigProperties {

    private String apiBasePath = "/api";
    private String publicGatewayOrigin = "";
    private String websocketUrl = "";
    private boolean analyticsEnabled;
    private double analyticsSampleRate;
    private String releaseChannel = "local";
    private final Map<String, Boolean> features = new LinkedHashMap<>();
    private final Upload upload = new Upload();

    public String getApiBasePath() {
        return apiBasePath;
    }

    public void setApiBasePath(String apiBasePath) {
        this.apiBasePath = apiBasePath;
    }

    public String getPublicGatewayOrigin() {
        return publicGatewayOrigin;
    }

    public void setPublicGatewayOrigin(String publicGatewayOrigin) {
        this.publicGatewayOrigin = publicGatewayOrigin;
    }

    public String getWebsocketUrl() {
        return websocketUrl;
    }

    public void setWebsocketUrl(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }

    public boolean isAnalyticsEnabled() {
        return analyticsEnabled;
    }

    public void setAnalyticsEnabled(boolean analyticsEnabled) {
        this.analyticsEnabled = analyticsEnabled;
    }

    public double getAnalyticsSampleRate() {
        return analyticsSampleRate;
    }

    public void setAnalyticsSampleRate(double analyticsSampleRate) {
        this.analyticsSampleRate = analyticsSampleRate;
    }

    public String getReleaseChannel() {
        return releaseChannel;
    }

    public void setReleaseChannel(String releaseChannel) {
        this.releaseChannel = releaseChannel;
    }

    public Map<String, Boolean> getFeatures() {
        return features;
    }

    public Upload getUpload() {
        return upload;
    }

    public static class Upload {

        private String maxFileSize = "10GB";
        private String maxRequestSize = "10GB";
        private List<String> allowedMimeTypes = new ArrayList<>();
        private List<String> allowedExtensions = new ArrayList<>();
        private boolean avatarUploadEnabled = true;
        private boolean mediaUploadEnabled = true;

        public String getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(String maxFileSize) {
            this.maxFileSize = normalizeText(maxFileSize, "10GB");
        }

        public String getMaxRequestSize() {
            return maxRequestSize;
        }

        public void setMaxRequestSize(String maxRequestSize) {
            this.maxRequestSize = normalizeText(maxRequestSize, "10GB");
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

        private static String normalizeText(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        private static List<String> normalizeList(List<String> values) {
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
}
