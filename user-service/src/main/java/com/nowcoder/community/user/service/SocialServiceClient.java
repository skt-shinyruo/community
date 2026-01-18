package com.nowcoder.community.user.service;

import com.nowcoder.community.common.api.Result;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class SocialServiceClient {

    private static final String BASE_URL = "http://social-service";

    private final RestTemplate restTemplate;

    public SocialServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public long safeUserLikeCount(int userId) {
        try {
            return userLikeCount(userId);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public long safeFolloweeCount(int userId) {
        try {
            return followeeCount(userId);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public long safeFollowerCount(int userId) {
        try {
            return followerCount(userId);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public boolean safeHasFollowed(String authorizationHeader, int targetUserId) {
        try {
            Boolean v = hasFollowed(authorizationHeader, targetUserId);
            return Boolean.TRUE.equals(v);
        } catch (Exception ignored) {
            return false;
        }
    }

    public long userLikeCount(int userId) {
        String url = BASE_URL + "/api/likes/users/" + userId + "/count";
        Result<Long> result = get(url, new ParameterizedTypeReference<Result<Long>>() {
        });
        Long data = result == null ? null : result.getData();
        return data == null ? 0 : data;
    }

    public long followeeCount(int userId) {
        String url = BASE_URL + "/api/follows/" + userId + "/followees/count?entityType=3";
        Result<Long> result = get(url, new ParameterizedTypeReference<Result<Long>>() {
        });
        Long data = result == null ? null : result.getData();
        return data == null ? 0 : data;
    }

    public long followerCount(int userId) {
        String url = BASE_URL + "/api/follows/" + userId + "/followers/count?entityType=3";
        Result<Long> result = get(url, new ParameterizedTypeReference<Result<Long>>() {
        });
        Long data = result == null ? null : result.getData();
        return data == null ? 0 : data;
    }

    public Boolean hasFollowed(String authorizationHeader, int targetUserId) {
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

