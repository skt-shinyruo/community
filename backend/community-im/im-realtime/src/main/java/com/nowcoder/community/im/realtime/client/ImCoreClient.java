package com.nowcoder.community.im.realtime.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class ImCoreClient {

    private final WebClient webClient;

    @Autowired
    public ImCoreClient(@Qualifier("imCoreWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Flux<UUID> listAllRoomIdsForUser(UUID userId, String bearerAccessToken, String traceId) {
        return fetchRoomIdPage(userId, null, 1000, bearerAccessToken, traceId)
                .expand(page -> {
                    if (page == null || !page.hasMore || page.nextCursorExclusive == null) {
                        return Mono.empty();
                    }
                    return fetchRoomIdPage(userId, page.nextCursorExclusive, 1000, bearerAccessToken, traceId);
                })
                .flatMapIterable(page -> page == null || page.roomIds == null ? List.<UUID>of() : page.roomIds)
                .filter(id -> id != null);
    }

    private Mono<RoomIdPage> fetchRoomIdPage(UUID userId, UUID cursorExclusive, int limit, String bearerAccessToken, String traceId) {
        RequestHeadersSpec<?> request = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/internal/im/realtime/users/{userId}/rooms")
                            .queryParam("limit", limit);
                    if (cursorExclusive != null) {
                        builder.queryParam("cursor", cursorExclusive);
                    }
                    return builder.build(userId);
                })
                .header("Authorization", "Bearer " + bearerAccessToken);

        applyTraceHeaders(request, traceId);

        return request
                .retrieve()
                .bodyToMono(RoomIdPage.class)
                .timeout(Duration.ofSeconds(3));
    }

    private static void applyTraceHeaders(RequestHeadersSpec<?> request, String traceId) {
        String normalizedTraceId = TraceIdCodec.normalizeTraceId(traceId);
        if (normalizedTraceId == null) {
            return;
        }
        request.header(TraceHeaders.HEADER_TRACE_ID, normalizedTraceId);
        request.header(TraceHeaders.HEADER_TRACEPARENT, TraceIdCodec.buildTraceparent(normalizedTraceId));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoomIdPage {
        public List<UUID> roomIds;
        public UUID nextCursorExclusive;
        public boolean hasMore;

        public RoomIdPage() {
        }

        public RoomIdPage(List<UUID> roomIds, UUID nextCursorExclusive, boolean hasMore) {
            this.roomIds = roomIds;
            this.nextCursorExclusive = nextCursorExclusive;
            this.hasMore = hasMore;
        }
    }
}
