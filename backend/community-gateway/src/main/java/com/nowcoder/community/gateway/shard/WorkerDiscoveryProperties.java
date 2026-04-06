package com.nowcoder.community.gateway.shard;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.ws.discovery")
public class WorkerDiscoveryProperties {

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
