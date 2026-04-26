package com.nowcoder.community.infra.idempotency;

public record EffectiveIdempotencyKey(String value, Source source) {

    public enum Source {
        HEADER,
        BODY_FALLBACK,
        HEADER_BODY_EQUAL
    }
}
