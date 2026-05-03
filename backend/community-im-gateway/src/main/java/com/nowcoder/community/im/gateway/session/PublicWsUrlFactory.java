package com.nowcoder.community.im.gateway.session;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class PublicWsUrlFactory {

    private static final String HEADER_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String HEADER_FORWARDED_HOST = "X-Forwarded-Host";

    private final ImGatewaySessionProperties properties;

    public PublicWsUrlFactory(ImGatewaySessionProperties properties) {
        this.properties = properties;
    }

    public String build(ServerHttpRequest request) {
        String configuredUrl = properties.getPublicWsUrl();
        if (StringUtils.hasText(configuredUrl)) {
            return validatedConfiguredPublicWsUrl(configuredUrl);
        }

        URI requestUri = request == null ? URI.create("http://localhost") : request.getURI();
        HttpHeaders headers = request == null ? HttpHeaders.EMPTY : request.getHeaders();
        String scheme = toWebSocketScheme(firstForwardedValue(headers.getFirst(HEADER_FORWARDED_PROTO)));
        if (!StringUtils.hasText(scheme)) {
            scheme = toWebSocketScheme(requestUri.getScheme());
        }
        if (!StringUtils.hasText(scheme)) {
            scheme = "ws";
        }
        String authority = validatedAuthority(firstForwardedValue(headers.getFirst(HEADER_FORWARDED_HOST)));
        if (!StringUtils.hasText(authority)) {
            authority = validatedAuthority(requestUri.getRawAuthority());
        }
        if (!StringUtils.hasText(authority)) {
            authority = "localhost";
        }

        return scheme + "://" + authority + normalizedPublicWsPath();
    }

    private String normalizedPublicWsPath() {
        String path = properties.hasExplicitPublicWsPath()
                ? properties.getPublicWsPath()
                : properties.getWs().getPath();
        if (!StringUtils.hasText(path)) {
            return "/ws/im";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static String firstForwardedValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(0, comma).trim() : value.trim();
    }

    private static String validatedConfiguredPublicWsUrl(String value) {
        String trimmed = value.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (uri.isAbsolute()
                    && ("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme))
                    && StringUtils.hasText(validatedAuthority(uri.getRawAuthority()))) {
                return trimmed;
            }
        } catch (IllegalArgumentException ex) {
            throw invalidPublicWsUrl();
        }
        throw invalidPublicWsUrl();
    }

    private static IllegalArgumentException invalidPublicWsUrl() {
        return new IllegalArgumentException("im.gateway.publicWsUrl must be an absolute ws/wss URI with authority");
    }

    private static String toWebSocketScheme(String scheme) {
        if (!StringUtils.hasText(scheme)) {
            return "";
        }
        String normalized = scheme.trim().toLowerCase();
        if ("https".equals(normalized) || "wss".equals(normalized)) {
            return "wss";
        }
        if ("http".equals(normalized) || "ws".equals(normalized)) {
            return "ws";
        }
        return "";
    }

    private static String validatedAuthority(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String authority = value.trim();
        if (authority.contains("://") || containsForbiddenAuthorityCharacter(authority)) {
            return "";
        }
        if (authority.startsWith("[")) {
            return isValidBracketedAuthority(authority) && isParsableAuthority(authority) ? authority : "";
        }
        return isValidHostAuthority(authority) && isParsableAuthority(authority) ? authority : "";
    }

    private static boolean containsForbiddenAuthorityCharacter(String authority) {
        for (int index = 0; index < authority.length(); index++) {
            char ch = authority.charAt(index);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)
                    || ch == '/' || ch == '?' || ch == '#' || ch == '@' || ch == '\\') {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidBracketedAuthority(String authority) {
        int closingBracket = authority.indexOf(']');
        if (closingBracket <= 1
                || authority.indexOf('[', 1) >= 0
                || authority.indexOf(']', closingBracket + 1) >= 0) {
            return false;
        }
        if (closingBracket == authority.length() - 1) {
            return true;
        }
        if (authority.charAt(closingBracket + 1) != ':') {
            return false;
        }
        return isValidPort(authority.substring(closingBracket + 2));
    }

    private static boolean isValidHostAuthority(String authority) {
        if (authority.indexOf('[') >= 0 || authority.indexOf(']') >= 0) {
            return false;
        }
        int firstColon = authority.indexOf(':');
        if (firstColon < 0) {
            return StringUtils.hasText(authority);
        }
        if (authority.indexOf(':', firstColon + 1) >= 0 || firstColon == 0) {
            return false;
        }
        return isValidPort(authority.substring(firstColon + 1));
    }

    private static boolean isValidPort(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        int port = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch < '0' || ch > '9') {
                return false;
            }
            port = port * 10 + ch - '0';
            if (port > 65535) {
                return false;
            }
        }
        return port >= 1;
    }

    private static boolean isParsableAuthority(String authority) {
        try {
            URI uri = new URI("ws://" + authority);
            return StringUtils.hasText(uri.getRawAuthority()) && uri.getHost() != null;
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return false;
        }
    }
}
