package com.nowcoder.community.gateway.im;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayImEdgeRouteProperties.class)
public class GatewayImEdgeRouteConfig {

    private static final int IM_EDGE_ROUTE_ORDER = -100;
    private static final String RETAIN_FIRST = "RETAIN_FIRST";
    private static final List<String> CORS_RESPONSE_HEADERS = List.of(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "Access-Control-Allow-Headers",
            "Access-Control-Allow-Methods",
            "Access-Control-Expose-Headers",
            "Access-Control-Max-Age"
    );

    @Bean
    RouteLocator gatewayImEdgeRoutes(RouteLocatorBuilder builder, GatewayImEdgeRouteProperties properties) {
        String serviceId = properties.getServiceId();
        String sessionPath = properties.getSessionPath();
        String wsPath = properties.getWsPath();

        RouteLocatorBuilder.Builder routes = builder.routes();
        if (StringUtils.hasText(serviceId) && isPublicPath(sessionPath)) {
            routes.route("im-session-edge", route -> route
                    .order(IM_EDGE_ROUTE_ORDER)
                    .method(HttpMethod.POST)
                    .and()
                    .path(sessionPath)
                    .filters(filterSpec -> {
                        for (String header : CORS_RESPONSE_HEADERS) {
                            filterSpec.dedupeResponseHeader(header, RETAIN_FIRST);
                        }
                        return filterSpec;
                    })
                    .uri("lb://" + serviceId));
        }
        if (StringUtils.hasText(serviceId) && isPublicPath(wsPath)) {
            routes.route("im-ws-edge", route -> route
                    .order(IM_EDGE_ROUTE_ORDER)
                    .path(wsPath)
                    .and()
                    .header("Upgrade", "(?i)websocket")
                    .uri("lb:ws://" + serviceId));
        }
        return routes.build();
    }

    private static boolean isPublicPath(String path) {
        return StringUtils.hasText(path) && !path.trim().startsWith("/internal");
    }
}
