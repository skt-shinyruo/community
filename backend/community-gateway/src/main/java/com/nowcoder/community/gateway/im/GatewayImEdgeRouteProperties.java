package com.nowcoder.community.gateway.im;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "gateway.im-edge")
public class GatewayImEdgeRouteProperties {

    private static final String DEFAULT_SERVICE_ID = "community-im-gateway";
    private static final String DEFAULT_SESSION_PATH = "/api/im/sessions";
    private static final String DEFAULT_WS_PATH = "/ws/im";

    private String serviceId = DEFAULT_SERVICE_ID;
    private String sessionPath = DEFAULT_SESSION_PATH;
    private String wsPath = DEFAULT_WS_PATH;

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = normalizeText(serviceId, DEFAULT_SERVICE_ID);
    }

    public String getSessionPath() {
        return sessionPath;
    }

    public void setSessionPath(String sessionPath) {
        this.sessionPath = normalizePath(sessionPath, DEFAULT_SESSION_PATH);
    }

    public String getWsPath() {
        return wsPath;
    }

    public void setWsPath(String wsPath) {
        this.wsPath = normalizePath(wsPath, DEFAULT_WS_PATH);
    }

    private static String normalizeText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private static String normalizePath(String value, String defaultValue) {
        String normalized = normalizeText(value, defaultValue);
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
