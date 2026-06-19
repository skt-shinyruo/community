package com.nowcoder.community.auth.application.result;

public record TokenFreshnessResult(Status status) {

    public enum Status {
        ACCEPTED,
        STALE,
        DENIED
    }

    public static TokenFreshnessResult accepted() {
        return new TokenFreshnessResult(Status.ACCEPTED);
    }

    public static TokenFreshnessResult stale() {
        return new TokenFreshnessResult(Status.STALE);
    }

    public static TokenFreshnessResult denied() {
        return new TokenFreshnessResult(Status.DENIED);
    }
}
