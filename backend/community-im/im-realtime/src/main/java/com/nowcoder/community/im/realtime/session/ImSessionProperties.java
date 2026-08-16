package com.nowcoder.community.im.realtime.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.session")
public class ImSessionProperties {

    private String workerServiceId = "im-realtime-worker";
    private String workerId = "local";
    private String workerIdMetadataKey = "workerId";

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

    public String getWorkerIdMetadataKey() {
        return workerIdMetadataKey;
    }

    public void setWorkerIdMetadataKey(String workerIdMetadataKey) {
        this.workerIdMetadataKey = workerIdMetadataKey;
    }

}
