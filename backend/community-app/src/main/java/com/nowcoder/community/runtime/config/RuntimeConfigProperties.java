package com.nowcoder.community.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
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
}
