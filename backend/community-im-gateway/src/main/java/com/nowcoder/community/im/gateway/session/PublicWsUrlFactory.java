package com.nowcoder.community.im.gateway.session;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class PublicWsUrlFactory {

    private final ImGatewaySessionProperties properties;

    public PublicWsUrlFactory(ImGatewaySessionProperties properties) {
        this.properties = properties;
    }

    public String build(ServerHttpRequest request) {
        String configuredUrl = properties.getPublicWsUrl();
        if (StringUtils.hasText(configuredUrl)) {
            return validatedConfiguredPublicWsUrl(configuredUrl);
        }
        throw invalidPublicWsUrl();
    }

    private static String validatedConfiguredPublicWsUrl(String value) {
        String trimmed = value.trim();
        try {
            URI uri = URI.create(trimmed).parseServerAuthority();
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if (uri.isAbsolute()
                    && ("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme))
                    && StringUtils.hasText(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && !uri.getRawAuthority().endsWith(":")
                    && (port == -1 || port >= 1 && port <= 65535)) {
                return trimmed;
            }
        } catch (IllegalArgumentException | URISyntaxException ex) {
            throw invalidPublicWsUrl();
        }
        throw invalidPublicWsUrl();
    }

    private static IllegalArgumentException invalidPublicWsUrl() {
        return new IllegalArgumentException("im.gateway.publicWsUrl must be an absolute ws/wss URI with authority");
    }

}
