package com.nowcoder.community.message.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.message.service.dto.UserSummary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class UserServiceClient {

    private static final String BASE_URL = "http://user-service";

    private final RestTemplate restTemplate;

    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Integer safeResolveUserIdByUsername(String username) {
        try {
            UserSummary u = resolveByUsername(username);
            return u == null || u.getId() <= 0 ? null : u.getId();
        } catch (Exception ignored) {
            return null;
        }
    }

    public UserSummary safeGetUser(int userId) {
        try {
            return getById(userId);
        } catch (Exception ignored) {
            return null;
        }
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

    private <T> Result<T> exchange(String url, HttpMethod method, HttpEntity<?> entity, ParameterizedTypeReference<Result<T>> typeRef) {
        try {
            ResponseEntity<Result<T>> resp = restTemplate.exchange(url, method, entity, typeRef);
            return resp.getBody();
        } catch (RestClientException e) {
            throw e;
        }
    }
}

