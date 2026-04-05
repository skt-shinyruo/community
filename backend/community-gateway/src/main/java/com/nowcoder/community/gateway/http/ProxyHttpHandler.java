package com.nowcoder.community.gateway.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@Component
public class ProxyHttpHandler {

    private final WebClient webClient;

    public ProxyHttpHandler(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<ServerResponse> proxy(ServerRequest request, UpstreamPool pool) {
        if (request == null || pool == null || pool.upstreams().isEmpty()) {
            return ServerResponse.notFound().build();
        }
        return request.bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .flatMap(body -> proxySequentially(request, pool.nextCandidates(), body, 0));
    }

    private Mono<ServerResponse> proxySequentially(ServerRequest request, List<URI> candidates, byte[] body, int index) {
        if (request == null || candidates == null || index >= candidates.size()) {
            return ServerResponse.status(HttpStatus.BAD_GATEWAY).build();
        }
        URI upstreamUri = buildTargetUri(request.exchange().getRequest(), candidates.get(index));
        return webClient
                .method(request.method())
                .uri(upstreamUri)
                .headers(headers -> copyRequestHeaders(request.headers().asHttpHeaders(), headers))
                .body(BodyInserters.fromValue(body))
                .exchangeToMono(response -> toServerResponse(
                        response.statusCode(),
                        response.headers().asHttpHeaders(),
                        response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                ))
                .onErrorResume(ex -> proxySequentially(request, candidates, body, index + 1));
    }

    private static URI buildTargetUri(ServerHttpRequest request, URI baseUri) {
        String path = request == null ? "" : request.getPath().value();
        if (!StringUtils.hasText(path)) {
            path = "/";
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String query = request == null ? null : request.getURI().getRawQuery();
        StringBuilder sb = new StringBuilder(String.valueOf(baseUri).replaceAll("/$", ""));
        sb.append(path);
        if (StringUtils.hasText(query)) {
            sb.append('?').append(query);
        }
        return URI.create(sb.toString());
    }

    private static void copyRequestHeaders(HttpHeaders from, HttpHeaders to) {
        from.forEach((name, values) -> {
            if (HttpHeaders.HOST.equalsIgnoreCase(name) || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                return;
            }
            to.put(name, values);
        });
    }

    private static Mono<ServerResponse> toServerResponse(
            HttpStatusCode status,
            HttpHeaders upstreamHeaders,
            Mono<byte[]> bodyMono
    ) {
        return bodyMono.flatMap(body -> {
            ServerResponse.BodyBuilder builder = ServerResponse.status(status);
            upstreamHeaders.forEach((name, values) -> {
                if (HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name) || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                    return;
                }
                builder.header(name, values.toArray(String[]::new));
            });
            return builder.bodyValue(body);
        });
    }
}
