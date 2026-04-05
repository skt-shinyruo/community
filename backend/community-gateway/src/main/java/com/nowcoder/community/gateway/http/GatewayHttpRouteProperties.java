package com.nowcoder.community.gateway.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.http")
public class GatewayHttpRouteProperties {

    private final List<Route> routes = new ArrayList<>();

    public List<Route> getRoutes() {
        return routes;
    }

    public static class Route {

        private String id;
        private String pathPrefix;
        private String serviceId;

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

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }
    }
}
