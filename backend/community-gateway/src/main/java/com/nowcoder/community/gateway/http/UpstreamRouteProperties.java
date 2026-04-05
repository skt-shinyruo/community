package com.nowcoder.community.gateway.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.http")
public class UpstreamRouteProperties {

    private final List<Route> routes = new ArrayList<>();

    public List<Route> getRoutes() {
        return routes;
    }

    public static class Route {

        private String id;
        private String pathPrefix;
        private URI uri;
        private final List<URI> uris = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPathPrefix() {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }

        public URI getUri() {
            return uri;
        }

        public void setUri(URI uri) {
            this.uri = uri;
        }

        public List<URI> getUris() {
            return uris;
        }
    }
}
