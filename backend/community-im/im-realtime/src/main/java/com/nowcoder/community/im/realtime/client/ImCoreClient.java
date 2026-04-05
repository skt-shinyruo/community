package com.nowcoder.community.im.realtime.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
public class ImCoreClient {

    private final BaseUrlPool baseUrlPool;

    @Autowired
    public ImCoreClient(Environment environment, @Value("${im.core.base-url}") String baseUrl) {
        this(
                BaseUrlPool.from(
                        "im.core",
                        baseUrl,
                        environment == null
                                ? List.of()
                                : Binder.get(environment)
                                        .bind("im.core.base-urls", Bindable.listOf(String.class))
                                        .orElse(List.of())
                )
        );
    }

    public ImCoreClient(String baseUrl) {
        this(BaseUrlPool.from("im.core", baseUrl, List.of()));
    }

    public ImCoreClient(List<String> baseUrls) {
        this(BaseUrlPool.from("im.core", baseUrls));
    }

    private ImCoreClient(BaseUrlPool baseUrlPool) {
        this.baseUrlPool = baseUrlPool;
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
        return fetchRoomIdPage(userId, cursorExclusive, limit, bearerAccessToken, traceId, baseUrlPool.nextCandidates(), 0);
    }

    private Mono<RoomIdPage> fetchRoomIdPage(
            int userId,
            long cursorExclusive,
            int limit,
            String bearerAccessToken,
            String traceId,
            List<String> candidates,
            int index
    ) {
        if (candidates == null || index >= candidates.size()) {
            return Mono.error(new IllegalStateException("im.core base URL pool exhausted"));
        }

        WebClient webClient = WebClient.builder()
                .baseUrl(candidates.get(index))
                .build();
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
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(ex -> fetchRoomIdPage(userId, cursorExclusive, limit, bearerAccessToken, traceId, candidates, index + 1));
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
