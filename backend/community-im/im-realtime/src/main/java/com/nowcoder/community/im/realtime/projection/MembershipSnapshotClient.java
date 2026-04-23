package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;
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

    public MembershipSnapshotClient(
            @Qualifier("membershipSnapshotWebClient") WebClient webClient,
            ImServiceClientProperties properties,
            ImSessionProperties sessionProperties,
            JwtProperties jwtProperties
    ) {
        this.webClient = webClient;
        this.timeout = Duration.ofMillis(Math.max(100L, properties.getSnapshotTimeoutMs()));
        this.jwtEncoder = JwtCodecs.jwtEncoder(jwtProperties);
        this.issuer = JwtCodecs.resolvedIssuer(jwtProperties);
        this.subject = StringUtils.hasText(sessionProperties.getWorkerId()) ? sessionProperties.getWorkerId().trim() : "im-realtime";
        this.internalScope = properties.getInternalScope();
    }

    public Flux<RoomMembershipEntry> fetchAll() {
        return fetchPage(null, null)
                .expand(page -> {
                    if (page == null || !page.hasMore() || page.nextRoomId() == null || page.nextUserId() == null) {
                        return Mono.empty();
                    }
                    return fetchPage(page.nextRoomId(), page.nextUserId());
                })
                .flatMapIterable(page -> page == null || page.entries() == null ? List.<RoomMembershipEntry>of() : page.entries());
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
                .timeout(timeout);
    }

    private String internalBearer() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", internalScope)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
