package com.nowcoder.community.im.realtime.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nowcoder.community.im.realtime.trace.TraceHeaders;
import com.nowcoder.community.im.realtime.trace.TraceIdCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
public class ImCoreClient {

    private final WebClient webClient;

    public ImCoreClient(@Value("${im.core.base-url}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(String.valueOf(baseUrl))
                .build();
    }

    public Flux<Long> listAllRoomIdsForUser(int userId, String bearerAccessToken, String traceId) {
        return fetchRoomIdPage(userId, 0L, 1000, bearerAccessToken, traceId)
                .expand(page -> {
                    if (page == null || !page.hasMore || page.nextCursorExclusive <= 0) {
                        return Mono.empty();
                    }
                    return fetchRoomIdPage(userId, page.nextCursorExclusive, 1000, bearerAccessToken, traceId);
                })
                .flatMapIterable(page -> page == null || page.roomIds == null ? List.<Long>of() : page.roomIds)
                .filter(id -> id != null && id > 0)
                .map(Long::longValue);
    }

    private Mono<RoomIdPage> fetchRoomIdPage(int userId, long cursorExclusive, int limit, String bearerAccessToken, String traceId) {
        RequestHeadersSpec<?> request = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/im/realtime/users/{userId}/rooms")
                        .queryParam("cursor", cursorExclusive)
                        .queryParam("limit", limit)
                        .build(userId))
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
        public List<Long> roomIds;
        public long nextCursorExclusive;
        public boolean hasMore;

        public RoomIdPage() {
        }

        public RoomIdPage(List<Long> roomIds, long nextCursorExclusive, boolean hasMore) {
            this.roomIds = roomIds;
            this.nextCursorExclusive = nextCursorExclusive;
            this.hasMore = hasMore;
        }
    }
}
