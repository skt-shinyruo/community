package com.nowcoder.community.gateway.ws;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "gateway.ws.proxy")
public class WsProxyProperties {

    private String path = "/ws/im";
    private boolean authRequired;
    private URI defaultWorkerUri;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isAuthRequired() {
        return authRequired;
    }

    public void setAuthRequired(boolean authRequired) {
        this.authRequired = authRequired;
    }

    public URI getDefaultWorkerUri() {
        return defaultWorkerUri;
    }

    public void setDefaultWorkerUri(URI defaultWorkerUri) {
        this.defaultWorkerUri = defaultWorkerUri;
    }
}
