package com.nowcoder.community.user.service;

import com.nowcoder.community.common.api.Result;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class SocialServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SocialServiceClient.class);

    private static final String BASE_URL = "http://social-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    public SocialServiceClient(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
    }

    public long safeUserLikeCount(int userId) {
        return call("userLikeCount", () -> userLikeCountInternal(userId), () -> 0L);
    }

    public long safeFolloweeCount(int userId) {
        return call("followeeCount", () -> followeeCountInternal(userId), () -> 0L);
    }

    public long safeFollowerCount(int userId) {
        return call("followerCount", () -> followerCountInternal(userId), () -> 0L);
    }

    public boolean safeHasFollowed(String authorizationHeader, int targetUserId) {
        Boolean v = call("hasFollowed", () -> hasFollowedInternal(authorizationHeader, targetUserId), () -> Boolean.FALSE);
        return Boolean.TRUE.equals(v);
    }

    public long userLikeCount(int userId) {
        return call("userLikeCount", () -> userLikeCountInternal(userId), null);
    }

    private long userLikeCountInternal(int userId) {
        String url = BASE_URL + "/api/likes/users/" + userId + "/count";
        Result<Long> result = get(url, new ParameterizedTypeReference<Result<Long>>() {
        });
        Long data = result == null ? null : result.getData();
        return data == null ? 0 : data;
    }

    public long followeeCount(int userId) {
        return call("followeeCount", () -> followeeCountInternal(userId), null);
    }

    private long followeeCountInternal(int userId) {
        String url = BASE_URL + "/api/follows/" + userId + "/followees/count?entityType=3";
        Result<Long> result = get(url, new ParameterizedTypeReference<Result<Long>>() {
        });
        Long data = result == null ? null : result.getData();
        return data == null ? 0 : data;
    }

    public long followerCount(int userId) {
        return call("followerCount", () -> followerCountInternal(userId), null);
    }

    private long followerCountInternal(int userId) {
        String url = BASE_URL + "/api/follows/" + userId + "/followers/count?entityType=3";
        Result<Long> result = get(url, new ParameterizedTypeReference<Result<Long>>() {
        });
        Long data = result == null ? null : result.getData();
        return data == null ? 0 : data;
    }

    public Boolean hasFollowed(String authorizationHeader, int targetUserId) {
        return call("hasFollowed", () -> hasFollowedInternal(authorizationHeader, targetUserId), null);
    }

    private Boolean hasFollowedInternal(String authorizationHeader, int targetUserId) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return false;
        }
        String url = BASE_URL + "/api/follows/status?entityType=3&entityId=" + targetUserId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        Result<Boolean> result = exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<Result<Boolean>>() {
        });
        return result == null ? null : result.getData();
    }

    private <T> T call(String api, Supplier<T> supplier, Supplier<T> fallback) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            record(api, "success", start);
            return v;
        } catch (Exception e) {
            if (fallback != null) {
                record(api, "degraded", start);
                log.warn("[social-client] degraded (api={}): {}", api, e.toString());
                return fallback.get();
            }
            record(api, "error", start);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("social-service 调用失败(api=" + api + ")", e);
        }
    }

    private void record(String api, String outcome, long startNanos) {
        Tags tags = Tags.of("api", api, "outcome", outcome);
        meterRegistry.counter("user_social_client_requests_total", tags).increment();
        meterRegistry.timer("user_social_client_latency", tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private <T> Result<T> get(String url, ParameterizedTypeReference<Result<T>> typeRef) {
        return exchange(url, HttpMethod.GET, HttpEntity.EMPTY, typeRef);
    }

    private <T> Result<T> exchange(String url, HttpMethod method, HttpEntity<?> entity, ParameterizedTypeReference<Result<T>> typeRef) {
        try {
            ResponseEntity<Result<T>> resp = restTemplate.exchange(url, method, entity, typeRef);
            return resp.getBody();
        } catch (RestClientException e) {
            throw e;
        }
    }
}
