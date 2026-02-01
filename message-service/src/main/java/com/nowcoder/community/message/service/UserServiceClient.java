package com.nowcoder.community.message.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import com.nowcoder.community.message.service.dto.UserSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private static final String METRIC_CLIENT = "message-service:user-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final String internalToken;
    private final boolean failOpen;

    public UserServiceClient(
            RestTemplate restTemplate,
            MeterRegistry meterRegistry,
            @Value("${clients.user.base-url:http://user-service}") String baseUrl,
            @Value("${clients.user.internal-token:}") String internalToken,
            @Value("${clients.user.fail-open:false}") boolean failOpen
    ) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.failOpen = failOpen;
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
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/users/resolve")
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
        String url = baseUrl + "/api/users/" + userId;
        Result<UserSummary> result = exchange(url, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<Result<UserSummary>>() {
        });
        return result == null ? null : result.getData();
    }

    public Map<Integer, UserSummary> safeBatchGetUsers(Set<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return call("batchSummary", () -> batchSummary(userIds), () -> Map.of());
    }

    private Map<Integer, UserSummary> batchSummary(Set<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = userIds.stream().filter(id -> id != null && id > 0).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        BatchUserSummaryRequest req = new BatchUserSummaryRequest();
        req.setUserIds(ids);

        String url = baseUrl + "/internal/users/batch-summary";
        ResponseEntity<Result<List<UserSummary>>> resp = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(req, InternalClientSupport.jsonHeaders(internalToken, "user-service")),
                new ParameterizedTypeReference<Result<List<UserSummary>>>() {
                }
        );
        List<UserSummary> list = InternalClientSupport.unwrap(resp, "user-service");
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        Map<Integer, UserSummary> map = new HashMap<>(list.size());
        for (UserSummary u : list) {
            if (u != null && u.getId() > 0) {
                map.put(u.getId(), u);
            }
        }
        return map;
    }

    private <T> T call(String api, Supplier<T> supplier, Supplier<T> fallback) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, InternalClientSupport.OUTCOME_SUCCESS, start);
            return v;
        } catch (Exception e) {
            if (fallback != null && failOpen) {
                InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[user-client] degraded (api={}): {}", api, e.toString());
                return fallback.get();
            }
            String outcome = InternalClientSupport.isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, outcome, start);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("user-service 调用失败(api=" + api + ")", e);
        }
    }

    private <T> Result<T> exchange(String url, HttpMethod method, HttpEntity<?> entity, ParameterizedTypeReference<Result<T>> typeRef) {
        try {
            ResponseEntity<Result<T>> resp = restTemplate.exchange(url, method, entity, typeRef);
            return resp.getBody();
        } catch (RestClientException e) {
            throw e;
        }
    }

    public static class BatchUserSummaryRequest {
        private List<Integer> userIds;

        public List<Integer> getUserIds() {
            return userIds == null ? List.of() : userIds;
        }

        public void setUserIds(List<Integer> userIds) {
            this.userIds = userIds;
        }
    }
}
