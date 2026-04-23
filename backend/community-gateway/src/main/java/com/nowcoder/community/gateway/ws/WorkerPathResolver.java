package com.nowcoder.community.gateway.ws;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Component
public class WorkerPathResolver {

    private final WsProxyProperties properties;

    public WorkerPathResolver(WsProxyProperties properties) {
        this.properties = properties;
    }

    public String resolve(URI uri) {
        String path = uri == null ? "" : String.valueOf(uri.getPath());
        String prefix = workerPathPrefix();
        if (!StringUtils.hasText(path) || !path.startsWith(prefix) || path.length() <= prefix.length()) {
            throw new IllegalArgumentException("workerId missing from path: " + path);
        }
        String workerId = path.substring(prefix.length()).trim();
        if (!StringUtils.hasText(workerId) || workerId.contains("/")) {
            throw new IllegalArgumentException("workerId missing from path: " + path);
        }
        return workerId;
    }

    private String workerPathPrefix() {
        String configured = properties.getPath();
        String normalized = StringUtils.hasText(configured) ? configured.trim() : "/ws/im/workers/**";
        if (normalized.endsWith("/**")) {
            return normalized.substring(0, normalized.length() - 2);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
