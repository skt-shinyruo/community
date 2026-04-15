package com.nowcoder.community.gateway.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayHttpRouteProperties.class)
public class GatewayRouteLocatorConfig {

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
    RouteLocator gatewayHttpRoutes(RouteLocatorBuilder builder, GatewayHttpRouteProperties properties) {
        List<GatewayHttpRouteProperties.Route> routes = new ArrayList<>(properties.getRoutes());
        routes.sort(Comparator.comparingInt(GatewayRouteLocatorConfig::pathPrefixLength).reversed());

        RouteLocatorBuilder.Builder spec = builder.routes();
        for (GatewayHttpRouteProperties.Route route : routes) {
            if (route == null || !isPublicPrefix(route.getPathPrefix()) || !StringUtils.hasText(route.getServiceId())) {
                continue;
            }
            String pathPrefix = normalizePrefix(route.getPathPrefix());
            spec.route(route.getId(), predicateSpec -> predicateSpec
                    .path(pathPrefix, pathPrefix + "/**")
                    .filters(filterSpec -> {
                        // Gateway owns browser-facing CORS, so collapse any duplicate downstream headers.
                        for (String header : CORS_RESPONSE_HEADERS) {
                            filterSpec.dedupeResponseHeader(header, RETAIN_FIRST);
                        }
                        return filterSpec;
                    })
                    .uri("lb://" + route.getServiceId().trim()));
        }
        return spec.build();
    }

    private static boolean isPublicPrefix(String pathPrefix) {
        return StringUtils.hasText(pathPrefix) && !pathPrefix.trim().startsWith("/internal");
    }

    private static int pathPrefixLength(GatewayHttpRouteProperties.Route route) {
        if (route == null || route.getPathPrefix() == null) {
            return 0;
        }
        return route.getPathPrefix().trim().length();
    }

    private static String normalizePrefix(String pathPrefix) {
        String normalized = pathPrefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
