package com.nowcoder.community.gateway.edge;

public interface RateLimiter {

    boolean allow(String key, RateLimitProperties.Policy policy);
}
