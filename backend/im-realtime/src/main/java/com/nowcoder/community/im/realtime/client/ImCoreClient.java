package com.nowcoder.community.im.realtime.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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

    public Flux<Long> listAllRoomIdsForUser(int userId, String bearerAccessToken) {
        return fetchRoomIdPage(userId, 0L, 1000, bearerAccessToken)
                .expand(page -> {
                    if (page == null || !page.hasMore || page.nextCursorExclusive <= 0) {
                        return Mono.empty();
                    }
                    return fetchRoomIdPage(userId, page.nextCursorExclusive, 1000, bearerAccessToken);
                })
                .flatMapIterable(page -> page == null || page.roomIds == null ? List.<Long>of() : page.roomIds)
                .filter(id -> id != null && id > 0)
                .map(Long::longValue);
    }

    private Mono<RoomIdPage> fetchRoomIdPage(int userId, long cursorExclusive, int limit, String bearerAccessToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/im/realtime/users/{userId}/rooms")
                        .queryParam("cursor", cursorExclusive)
                        .queryParam("limit", limit)
                        .build(userId))
                .header("Authorization", "Bearer " + bearerAccessToken)
                .retrieve()
                .bodyToMono(RoomIdPage.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> Mono.just(new RoomIdPage(List.of(), cursorExclusive, false)));
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
