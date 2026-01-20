package com.nowcoder.community.message.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.message.service.dto.UserSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private static final String BASE_URL = "http://user-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    public UserServiceClient(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
    }

    public Integer safeResolveUserIdByUsername(String username) {
        return call("resolveByUsername", () -> {
            UserSummary u = resolveByUsername(username);
            return u == null || u.getId() <= 0 ? null : u.getId();
        }, () -> null);
    }

    public UserSummary safeGetUser(int userId) {
        return call("getById", () -> getById(userId), () -> null);
    }

    public UserSummary resolveByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/api/users/resolve")
                .queryParam("username", username)
                .toUriString();
        Result<UserSummary> result = exchange(url, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<Result<UserSummary>>() {
        });
        return result == null ? null : result.getData();
    }

    public UserSummary getById(int userId) {
        if (userId <= 0) {
            return null;
        }
        String url = BASE_URL + "/api/users/" + userId;
        Result<UserSummary> result = exchange(url, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<Result<UserSummary>>() {
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
                log.warn("[user-client] degraded (api={}): {}", api, e.toString());
                return fallback.get();
            }
            record(api, "error", start);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("user-service 调用失败(api=" + api + ")", e);
        }
    }

    private void record(String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("api", api, "outcome", outcome);
        meterRegistry.counter("message_user_client_requests_total", tags).increment();
        meterRegistry.timer("message_user_client_latency", tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
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
