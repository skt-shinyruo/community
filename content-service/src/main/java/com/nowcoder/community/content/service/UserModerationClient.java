// user-service 内部治理接口客户端：用于禁言/封禁与状态查询（通过 X-Internal-Token 保护）。
package com.nowcoder.community.content.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

@Service
public class UserModerationClient {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;

    public UserModerationClient(
            RestTemplate restTemplate,
            @Value("${clients.user.base-url:http://user-service}") String baseUrl,
            @Value("${clients.user.internal-token:${INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    public ModerationStatus getStatus(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String url = baseUrl + "/internal/users/" + userId + "/moderation-status";
        Result<ModerationStatus> result = exchange(url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()), new ParameterizedTypeReference<Result<ModerationStatus>>() {
        });
        return requireOk(result);
    }

    public void mute(int userId, int durationSeconds) {
        apply(userId, "mute", durationSeconds);
    }

    public void ban(int userId, int durationSeconds) {
        apply(userId, "ban", durationSeconds);
    }

    private void apply(int userId, String action, int durationSeconds) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (!StringUtils.hasText(action)) {
            throw new BusinessException(INVALID_ARGUMENT, "action 不能为空");
        }
        int seconds = Math.max(0, durationSeconds);

        ModerationApplyRequest req = new ModerationApplyRequest();
        req.setAction(action);
        req.setDurationSeconds(seconds);

        String url = baseUrl + "/internal/users/" + userId + "/moderation";
        Result<ModerationStatus> result = exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(req, jsonHeaders()),
                new ParameterizedTypeReference<Result<ModerationStatus>>() {
                }
        );
        requireOk(result);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
        if (StringUtils.hasText(internalToken)) {
            headers.set(HEADER_INTERNAL_TOKEN, internalToken);
        }
        return headers;
    }

    private <T> T requireOk(Result<T> result) {
        if (result == null) {
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 响应为空");
        }
        if (result.getCode() != 0) {
            throw new BusinessException(INTERNAL_ERROR, "user-service 返回错误：" + result.getMessage());
        }
        return result.getData();
    }

    private <T> Result<T> exchange(String url, HttpMethod method, HttpEntity<?> entity, ParameterizedTypeReference<Result<T>> typeRef) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(INVALID_ARGUMENT, "url 不能为空");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(FORBIDDEN, "internal-token 未配置（请设置 env: INTERNAL_TOKEN / USER_INTERNAL_TOKEN）");
        }
        try {
            ResponseEntity<Result<T>> resp = restTemplate.exchange(url, method, entity, typeRef);
            return resp.getBody();
        } catch (RestClientException e) {
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    public static class ModerationStatus {
        private int userId;
        private Instant muteUntil;
        private Instant banUntil;

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public Instant getMuteUntil() {
            return muteUntil;
        }

        public void setMuteUntil(Instant muteUntil) {
            this.muteUntil = muteUntil;
        }

        public Instant getBanUntil() {
            return banUntil;
        }

        public void setBanUntil(Instant banUntil) {
            this.banUntil = banUntil;
        }
    }

    public static class ModerationApplyRequest {
        private String action;
        private int durationSeconds;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }
    }
}

