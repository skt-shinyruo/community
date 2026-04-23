package com.nowcoder.community.gateway.ws;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.ws.proxy")
public class WsProxyProperties {

    private String path = "/ws/im/workers/**";

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
