package com.nowcoder.community.gateway.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunctions.Builder;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UpstreamRouteProperties.class)
public class HttpProxyRouter {

    @Bean
    RouterFunction<ServerResponse> gatewayHttpRoutes(
            UpstreamRouteProperties properties,
            ProxyHttpHandler proxyHttpHandler
    ) {
        List<UpstreamRouteProperties.Route> routes = new ArrayList<>(properties.getRoutes());
        routes.sort(Comparator.comparingInt(HttpProxyRouter::prefixLength).reversed());

        Builder builder = RouterFunctions.route();
        for (UpstreamRouteProperties.Route route : routes) {
            if (route == null || route.getUri() == null || !isPublicPrefix(route.getPathPrefix())) {
                continue;
            }
            RequestPredicate predicate = pathPrefixPredicate(route.getPathPrefix());
            builder.route(predicate, request -> proxyHttpHandler.proxy(request, route));
        }
        return builder.build();
    }

    private static boolean isPublicPrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            return false;
        }
        return !pathPrefix.startsWith("/internal");
    }

    private static int prefixLength(UpstreamRouteProperties.Route route) {
        return route == null || route.getPathPrefix() == null ? 0 : route.getPathPrefix().length();
    }

    private static RequestPredicate pathPrefixPredicate(String prefix) {
        return request -> {
            String path = request == null ? "" : request.path();
            return path.equals(prefix) || path.startsWith(prefix + "/");
        };
    }
}
