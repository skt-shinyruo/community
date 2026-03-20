package com.nowcoder.community.gateway.shard;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.ws.shard")
public class WorkerRegistryProperties {

    private final List<Worker> workers = new ArrayList<>();

    public List<Worker> getWorkers() {
        return workers;
    }

    public static class Worker {

        private String id;
        private URI uri;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public URI getUri() {
            return uri;
        }

        public void setUri(URI uri) {
            this.uri = uri;
        }
    }
}
