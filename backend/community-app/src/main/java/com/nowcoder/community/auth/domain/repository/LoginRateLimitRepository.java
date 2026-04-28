package com.nowcoder.community.auth.domain.repository;

public interface LoginRateLimitRepository {

    int count(String key);

    int increment(String key, int windowSeconds);

    void delete(String key);
}
