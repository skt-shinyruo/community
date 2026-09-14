package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;
import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.realtime.client.ImServiceClientProperties;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class MembershipSnapshotClient {

    private static final int PAGE_LIMIT = 500;

    private final WebClient webClient;
    private final Duration timeout;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String subject;
    private final String internalScope;
    private final String audience;

    public MembershipSnapshotClient(
            @Qualifier("membershipSnapshotWebClient") WebClient webClient,
            ImServiceClientProperties properties,
            ImSessionProperties sessionProperties,
            JwtProperties jwtProperties
    ) {
        this.webClient = webClient;
        this.timeout = Duration.ofMillis(Math.max(100L, properties.getSnapshotTimeoutMs()));
        this.jwtEncoder = JwtCodecs.serviceTokenEncoder(jwtProperties);
        this.issuer = JwtCodecs.resolvedIssuer(jwtProperties);
        this.subject = StringUtils.hasText(sessionProperties.getWorkerId()) ? sessionProperties.getWorkerId().trim() : "im-realtime";
        this.internalScope = properties.getInternalScope();
        this.audience = properties.getMembershipSnapshotServiceId();
    }

    public Flux<RoomMembershipEntry> fetchAll() {
        return fetchSnapshot().flatMapIterable(FetchedMembershipSnapshot::entries);
    }

    public Mono<FetchedMembershipSnapshot> fetchSnapshot() {
        return fetchPage(null, null)
                .flatMapMany(firstPage -> {
                    long snapshotWatermark = pageWatermark(firstPage);
                    return Flux.just(firstPage).expand(page -> {
                        if (!page.hasMore()) {
                            return Mono.empty();
                        }
                        return fetchPage(page.nextRoomId(), page.nextUserId())
                                .map(nextPage -> requireSameWatermark(nextPage, snapshotWatermark));
                    });
                })
                .collectList()
                .map(pages -> new FetchedMembershipSnapshot(entries(pages), watermark(pages)));
    }

    private Mono<RoomMembershipSnapshot> fetchPage(UUID afterRoomId, UUID afterUserId) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/internal/im/realtime/projections/room-memberships")
                            .queryParam("limit", PAGE_LIMIT);
                    if (afterRoomId != null && afterUserId != null) {
                        builder.queryParam("afterRoomId", afterRoomId);
                        builder.queryParam("afterUserId", afterUserId);
                    }
                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, internalBearer())
                .retrieve()
                .bodyToMono(RoomMembershipSnapshot.class)
                .map(MembershipSnapshotClient::requireCompletePage)
                .map(page -> requireAdvancingCursor(page, afterRoomId, afterUserId))
                .timeout(timeout);
    }

    private static RoomMembershipSnapshot requireCompletePage(RoomMembershipSnapshot page) {
        if (page.entries() == null) {
            throw new IllegalStateException("room membership snapshot page omitted the entries list");
        }
        for (RoomMembershipEntry entry : page.entries()) {
            if (entry == null || entry.roomId() == null || entry.userId() == null) {
                throw new IllegalStateException(
                        "room membership snapshot page contained an entry without a complete roomId/userId identity");
            }
        }
        if (page.hasMore() && (page.nextRoomId() == null || page.nextUserId() == null)) {
            throw new IllegalStateException(
                    "room membership snapshot page declared hasMore without a complete continuation cursor");
        }
        return page;
    }

    private static RoomMembershipSnapshot requireAdvancingCursor(
            RoomMembershipSnapshot page,
            UUID afterRoomId,
            UUID afterUserId
    ) {
        if (!page.hasMore() || afterRoomId == null || afterUserId == null) {
            return page;
        }
        int roomOrder = compareUuidBytes(page.nextRoomId(), afterRoomId);
        if (roomOrder < 0 || (roomOrder == 0 && compareUuidBytes(page.nextUserId(), afterUserId) <= 0)) {
            throw new IllegalStateException("room membership snapshot continuation cursor did not advance");
        }
        return page;
    }

    private static int compareUuidBytes(UUID left, UUID right) {
        return Arrays.compareUnsigned(BinaryUuidCodec.toBytes(left), BinaryUuidCodec.toBytes(right));
    }

    private String internalBearer() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", internalScope)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type(JwtCodecs.SERVICE_TOKEN_TYPE)
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static List<RoomMembershipEntry> entries(List<RoomMembershipSnapshot> pages) {
        List<RoomMembershipEntry> entries = new ArrayList<>();
        for (RoomMembershipSnapshot page : pages) {
            entries.addAll(page.entries());
        }
        return List.copyOf(entries);
    }

    private static long watermark(List<RoomMembershipSnapshot> pages) {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalStateException("projection snapshot returned no pages");
        }
        return pageWatermark(pages.get(0));
    }

    private static long pageWatermark(RoomMembershipSnapshot page) {
        return ProjectionVersions.requireNonNegative(
                page == null ? null : page.snapshotHighWatermark(),
                "snapshotHighWatermark"
        );
    }

    private static RoomMembershipSnapshot requireSameWatermark(
            RoomMembershipSnapshot page,
            long expectedWatermark
    ) {
        if (pageWatermark(page) != expectedWatermark) {
            throw new IllegalStateException("projection snapshot watermark changed between pages");
        }
        return page;
    }

    public record FetchedMembershipSnapshot(List<RoomMembershipEntry> entries, long snapshotHighWatermark) {
    }
}
