package com.nowcoder.community.im.realtime.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.session")
public class ImSessionProperties {

    private String workerServiceId = "im-realtime-worker";
    private String workerId = "local";
    private String wsBaseUrl = "";
    private Duration ticketTtl = Duration.ofMinutes(2);
    private String workerIdMetadataKey = "workerId";
    private String wsPathMetadataKey = "wsPath";
    private String wsPortMetadataKey = "wsPort";

    public String getWorkerServiceId() {
        return workerServiceId;
    }

    public void setWorkerServiceId(String workerServiceId) {
        this.workerServiceId = workerServiceId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getWsBaseUrl() {
        return wsBaseUrl;
    }

    public void setWsBaseUrl(String wsBaseUrl) {
        this.wsBaseUrl = wsBaseUrl;
    }

    public Duration getTicketTtl() {
        return ticketTtl;
    }

    public void setTicketTtl(Duration ticketTtl) {
        this.ticketTtl = ticketTtl == null || ticketTtl.isNegative() || ticketTtl.isZero()
                ? Duration.ofMinutes(2)
                : ticketTtl;
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
