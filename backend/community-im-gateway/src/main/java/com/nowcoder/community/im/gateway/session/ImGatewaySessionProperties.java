package com.nowcoder.community.im.gateway.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.gateway")
public class ImGatewaySessionProperties {

    private static final String DEFAULT_WS_PATH = "/ws/im";

    private String publicWsUrl = "";
    private final Session session = new Session();
    private final Worker worker = new Worker();
    private final Ws ws = new Ws();

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

    }

    public static class Ws {
        private String path = DEFAULT_WS_PATH;
        private long firstFrameTimeoutMs = 5000L;
        private int maxInboundChars = 10_000;
        private int maxInboundBufferFrames = 64;

        public String getPath() {
            return normalizePath(path);
        }

        public void setPath(String path) {
            this.path = normalizePath(path);
        }

        public long getFirstFrameTimeoutMs() {
            return firstFrameTimeoutMs;
        }

        public void setFirstFrameTimeoutMs(long firstFrameTimeoutMs) {
            this.firstFrameTimeoutMs = firstFrameTimeoutMs <= 0 ? 5000L : firstFrameTimeoutMs;
        }

        public int getMaxInboundChars() {
            return maxInboundChars;
        }

        public void setMaxInboundChars(int maxInboundChars) {
            this.maxInboundChars = Math.min(Math.max(1, maxInboundChars), 100_000);
        }

        public int getMaxInboundBufferFrames() {
            return maxInboundBufferFrames;
        }

        public void setMaxInboundBufferFrames(int maxInboundBufferFrames) {
            this.maxInboundBufferFrames = Math.min(Math.max(1, maxInboundBufferFrames), 10_000);
        }
    }
}
