package com.nowcoder.community.im.gateway.security;

import reactor.core.publisher.Mono;

public interface AccessTokenFreshnessVerifier {

    Mono<Decision> verify(String accessToken);

    enum Decision {
        FRESH,
        STALE,
        DENIED,
        UNAVAILABLE
    }
}
