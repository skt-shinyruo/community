package com.nowcoder.community.im.core.policy;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OwnerApiPrivateMessagePolicyVerifier implements PrivateMessagePolicyVerifier {

    private final RestClient restClient;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String internalScope;
    private final Duration requestTimeout;
    private final Duration rejectionCacheTtl;
    private final int rejectionCacheMaxEntries;
    private final Map<DecisionKey, CachedDecision> cache = new ConcurrentHashMap<>();

    public OwnerApiPrivateMessagePolicyVerifier(
            @Qualifier("imPolicyRestClient") RestClient restClient,
            ImCorePolicyClientProperties properties,
            JwtProperties jwtProperties
    ) {
        this.restClient = restClient;
        this.jwtEncoder = JwtCodecs.jwtEncoder(jwtProperties);
        this.issuer = JwtCodecs.resolvedIssuer(jwtProperties);
        this.internalScope = properties.getInternalScope();
        this.requestTimeout = properties.normalizedRequestTimeout();
        this.rejectionCacheTtl = properties.normalizedRejectionCacheTtl();
        this.rejectionCacheMaxEntries = properties.normalizedRejectionCacheMaxEntries();
    }

    @Override
    public PrivateMessagePolicyDecision verify(UUID fromUserId, UUID toUserId) {
        if (fromUserId == null || toUserId == null) {
            return PrivateMessagePolicyDecision.deny(400, "invalid_request", "参数错误");
        }
        DecisionKey key = new DecisionKey(fromUserId, toUserId);
        CachedDecision cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMs() > now) {
            return cached.decision();
        }

        PrivateMessagePolicyDecision decision = fetchDecision(fromUserId, toUserId);
        cacheDecision(key, decision, now);
        return decision;
    }

    private PrivateMessagePolicyDecision fetchDecision(UUID fromUserId, UUID toUserId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/im/realtime/projections/private-message-decision")
                        .queryParam("fromUserId", fromUserId)
                        .queryParam("toUserId", toUserId)
                        .build())
                .header("Authorization", internalBearer())
                .retrieve()
                .body(PrivateMessagePolicyDecision.class);
    }

    private void cacheDecision(DecisionKey key, PrivateMessagePolicyDecision decision, long nowEpochMs) {
        if (decision == null || decision.allowed() || rejectionCacheTtl.isZero() || rejectionCacheTtl.isNegative()) {
            return;
        }
        if (cache.size() >= rejectionCacheMaxEntries) {
            cache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMs() <= nowEpochMs);
        }
        if (cache.size() >= rejectionCacheMaxEntries) {
            cache.clear();
        }
        cache.put(key, new CachedDecision(decision, nowEpochMs + rejectionCacheTtl.toMillis()));
    }

    private String internalBearer() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("im-core")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(Math.max(1L, requestTimeout.toSeconds() + 300L)))
                .claim("scope", StringUtils.hasText(internalScope) ? internalScope.trim() : "im.realtime.internal")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private record DecisionKey(UUID fromUserId, UUID toUserId) {
    }

    private record CachedDecision(PrivateMessagePolicyDecision decision, long expiresAtEpochMs) {
    }
}
