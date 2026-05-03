package com.nowcoder.community.im.gateway.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.gateway")
public class ImGatewaySessionProperties {

    private static final String DEFAULT_WS_PATH = "/ws/im";

    private String publicWsPath = "/ws/im";
    private String publicWsUrl = "";
    private final Session session = new Session();
    private final Worker worker = new Worker();
    private final Ws ws = new Ws();

    public String getPublicWsPath() {
        return publicWsPath;
    }

    public void setPublicWsPath(String publicWsPath) {
        this.publicWsPath = publicWsPath;
    }

    public String getPublicWsUrl() {
        return publicWsUrl;
    }

    public void setPublicWsUrl(String publicWsUrl) {
        this.publicWsUrl = publicWsUrl;
    }

    public Session getSession() {
        return session;
    }

    public Worker getWorker() {
        return worker;
    }

    public Ws getWs() {
        return ws;
    }

    private static String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return DEFAULT_WS_PATH;
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    public static class Session {
        private Duration ticketTtl = Duration.ofMinutes(2);

        public Duration getTicketTtl() {
            return ticketTtl;
        }

        public void setTicketTtl(Duration ticketTtl) {
            this.ticketTtl = ticketTtl == null || ticketTtl.isZero() || ticketTtl.isNegative()
                    ? Duration.ofMinutes(2)
                    : ticketTtl;
        }
    }

    public static class Worker {
        private String serviceId = "im-realtime-worker";
        private String workerIdMetadataKey = "workerId";
        private String wsPathMetadataKey = "wsPath";
        private String wsPortMetadataKey = "wsPort";

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getWorkerIdMetadataKey() {
            return workerIdMetadataKey;
        }

        public void setWorkerIdMetadataKey(String workerIdMetadataKey) {
            this.workerIdMetadataKey = workerIdMetadataKey;
        }

        public String getWsPathMetadataKey() {
            return wsPathMetadataKey;
        }

        public void setWsPathMetadataKey(String wsPathMetadataKey) {
            this.wsPathMetadataKey = wsPathMetadataKey;
        }

        public String getWsPortMetadataKey() {
            return wsPortMetadataKey;
        }

        public void setWsPortMetadataKey(String wsPortMetadataKey) {
            this.wsPortMetadataKey = wsPortMetadataKey;
        }
    }

    public static class Ws {
        private String path = DEFAULT_WS_PATH;

        public String getPath() {
            return normalizePath(path);
        }

        public void setPath(String path) {
            this.path = normalizePath(path);
        }
    }
}
