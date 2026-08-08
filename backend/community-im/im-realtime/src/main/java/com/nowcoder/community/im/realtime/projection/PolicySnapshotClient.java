package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
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
import java.util.List;
import java.util.UUID;

@Component
public class PolicySnapshotClient {

    private static final int PAGE_LIMIT = 500;

    private final WebClient webClient;
    private final Duration timeout;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String subject;
    private final String internalScope;
    private final String audience;

    public PolicySnapshotClient(
            @Qualifier("policySnapshotWebClient") WebClient webClient,
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
        this.audience = properties.getPolicySnapshotServiceId();
    }

    public Flux<UserMessagingPolicyEntry> fetchUserPolicies() {
        return fetchUserPolicySnapshot().flatMapIterable(FetchedUserPolicySnapshot::entries);
    }

    public Flux<UserBlockRelationEntry> fetchBlockRelations() {
        return fetchBlockRelationSnapshot().flatMapIterable(FetchedBlockRelationSnapshot::entries);
    }

    public Mono<FetchedUserPolicySnapshot> fetchUserPolicySnapshot() {
        return fetchUserPolicyPage(null, null)
                .flatMapMany(firstPage -> {
                    long snapshotVersion = userPolicyPageWatermark(firstPage);
                    return Flux.just(firstPage).expand(page -> {
                        if (!page.hasMore() || page.nextUserId() == null) {
                            return Mono.empty();
                        }
                        return fetchUserPolicyPage(page.nextUserId(), snapshotVersion)
                                .map(nextPage -> requireUserPolicyPageWatermark(nextPage, snapshotVersion));
                    });
                })
                .collectList()
                .map(pages -> new FetchedUserPolicySnapshot(userPolicyEntries(pages), userPolicyWatermark(pages)));
    }

    public Mono<FetchedBlockRelationSnapshot> fetchBlockRelationSnapshot() {
        return fetchBlockRelationPage(null, null, null)
                .flatMapMany(firstPage -> {
                    long snapshotVersion = blockRelationPageWatermark(firstPage);
                    return Flux.just(firstPage).expand(page -> {
                        if (!page.hasMore()
                                || page.nextBlockerUserId() == null || page.nextBlockedUserId() == null) {
                            return Mono.empty();
                        }
                        return fetchBlockRelationPage(
                                page.nextBlockerUserId(),
                                page.nextBlockedUserId(),
                                snapshotVersion
                        ).map(nextPage -> requireBlockRelationPageWatermark(nextPage, snapshotVersion));
                    });
                })
                .collectList()
                .map(pages -> new FetchedBlockRelationSnapshot(blockRelationEntries(pages), blockRelationWatermark(pages)));
    }

    private Mono<UserMessagingPolicySnapshot> fetchUserPolicyPage(UUID afterUserId, Long snapshotVersion) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/internal/im/realtime/projections/user-policies")
                            .queryParam("limit", PAGE_LIMIT);
                    if (afterUserId != null) {
                        builder.queryParam("afterUserId", afterUserId);
                    }
                    if (snapshotVersion != null) {
                        builder.queryParam("snapshotVersion", snapshotVersion);
                    }
                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, internalBearer())
                .retrieve()
                .bodyToMono(UserMessagingPolicySnapshot.class)
                .timeout(timeout);
    }

    private Mono<UserBlockRelationSnapshot> fetchBlockRelationPage(
            UUID afterBlockerUserId,
            UUID afterBlockedUserId,
            Long snapshotVersion
    ) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/internal/im/realtime/projections/block-relations")
                            .queryParam("limit", PAGE_LIMIT);
                    if (afterBlockerUserId != null && afterBlockedUserId != null) {
                        builder.queryParam("afterBlockerUserId", afterBlockerUserId);
                        builder.queryParam("afterBlockedUserId", afterBlockedUserId);
                    }
                    if (snapshotVersion != null) {
                        builder.queryParam("snapshotVersion", snapshotVersion);
                    }
                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, internalBearer())
                .retrieve()
                .bodyToMono(UserBlockRelationSnapshot.class)
                .timeout(timeout);
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

    private static List<UserMessagingPolicyEntry> userPolicyEntries(List<UserMessagingPolicySnapshot> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        List<UserMessagingPolicyEntry> entries = new ArrayList<>();
        for (UserMessagingPolicySnapshot page : pages) {
            if (page != null && page.entries() != null) {
                entries.addAll(page.entries());
            }
        }
        return List.copyOf(entries);
    }

    private static List<UserBlockRelationEntry> blockRelationEntries(List<UserBlockRelationSnapshot> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        List<UserBlockRelationEntry> entries = new ArrayList<>();
        for (UserBlockRelationSnapshot page : pages) {
            if (page != null && page.entries() != null) {
                entries.addAll(page.entries());
            }
        }
        return List.copyOf(entries);
    }

    private static long userPolicyWatermark(List<UserMessagingPolicySnapshot> pages) {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalStateException("projection snapshot returned no pages");
        }
        long watermark = userPolicyPageWatermark(pages.get(0));
        for (UserMessagingPolicySnapshot page : pages) {
            requireUserPolicyPageWatermark(page, watermark);
        }
        return watermark;
    }

    private static long blockRelationWatermark(List<UserBlockRelationSnapshot> pages) {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalStateException("projection snapshot returned no pages");
        }
        long watermark = blockRelationPageWatermark(pages.get(0));
        for (UserBlockRelationSnapshot page : pages) {
            requireBlockRelationPageWatermark(page, watermark);
        }
        return watermark;
    }

    private static long userPolicyPageWatermark(UserMessagingPolicySnapshot page) {
        return ProjectionVersions.requireNonNegative(
                page == null ? null : page.snapshotHighWatermark(),
                "snapshotHighWatermark"
        );
    }

    private static UserMessagingPolicySnapshot requireUserPolicyPageWatermark(
            UserMessagingPolicySnapshot page,
            long expectedWatermark
    ) {
        if (userPolicyPageWatermark(page) != expectedWatermark) {
            throw new IllegalStateException("projection snapshot watermark changed between pages");
        }
        return page;
    }

    private static long blockRelationPageWatermark(UserBlockRelationSnapshot page) {
        return ProjectionVersions.requireNonNegative(
                page == null ? null : page.snapshotHighWatermark(),
                "snapshotHighWatermark"
        );
    }

    private static UserBlockRelationSnapshot requireBlockRelationPageWatermark(
            UserBlockRelationSnapshot page,
            long expectedWatermark
    ) {
        if (blockRelationPageWatermark(page) != expectedWatermark) {
            throw new IllegalStateException("projection snapshot watermark changed between pages");
        }
        return page;
    }

    public record FetchedUserPolicySnapshot(List<UserMessagingPolicyEntry> entries, long snapshotHighWatermark) {
    }

    public record FetchedBlockRelationSnapshot(List<UserBlockRelationEntry> entries, long snapshotHighWatermark) {
    }
}
