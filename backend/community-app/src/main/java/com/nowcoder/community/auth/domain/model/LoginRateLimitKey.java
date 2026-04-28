package com.nowcoder.community.auth.domain.model;

public record LoginRateLimitKey(String username, String ip) {
}
