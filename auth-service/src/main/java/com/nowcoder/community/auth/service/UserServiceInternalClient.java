package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.UserServiceClientProperties;
import com.nowcoder.community.auth.service.dto.UserInternalActivateRequest;
import com.nowcoder.community.auth.service.dto.UserInternalActivationResponse;
import com.nowcoder.community.auth.service.dto.UserInternalAuthenticateRequest;
import com.nowcoder.community.auth.service.dto.UserInternalAuthenticateResponse;
import com.nowcoder.community.auth.service.dto.UserInternalRegisterRequest;
import com.nowcoder.community.auth.service.dto.UserInternalRegisterResponse;
import com.nowcoder.community.auth.service.dto.UserInternalSessionProfileResponse;
import com.nowcoder.community.auth.service.dto.UserInternalUpdatePasswordRequest;
import com.nowcoder.community.auth.service.dto.UserInternalUserByEmailResponse;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.api.SimpleErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class UserServiceInternalClient {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final UserServiceClientProperties properties;

    public UserServiceInternalClient(RestTemplate restTemplate, MeterRegistry meterRegistry, UserServiceClientProperties properties) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public UserInternalAuthenticateResponse authenticate(String username, String password) {
        return call("authenticate", () -> {
            UserInternalAuthenticateRequest req = new UserInternalAuthenticateRequest();
            req.setUsername(username);
            req.setPassword(password);
            String url = properties.getBaseUrl() + "/internal/users/authenticate";
            Result<UserInternalAuthenticateResponse> result = exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(req, jsonHeaders()),
                    new ParameterizedTypeReference<Result<UserInternalAuthenticateResponse>>() {
                    }
            );
            return requireOk(result);
        });
    }

    public UserInternalSessionProfileResponse sessionProfile(int userId) {
        return call("sessionProfile", () -> {
            String url = properties.getBaseUrl() + "/internal/users/" + userId + "/session-profile";
            Result<UserInternalSessionProfileResponse> result = exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()),
                    new ParameterizedTypeReference<Result<UserInternalSessionProfileResponse>>() {
                    }
            );
            return requireOk(result);
        });
    }

    public UserInternalRegisterResponse register(String username, String password, String email) {
        return call("register", () -> {
            UserInternalRegisterRequest req = new UserInternalRegisterRequest();
            req.setUsername(username);
            req.setPassword(password);
            req.setEmail(email);
            String url = properties.getBaseUrl() + "/internal/users/register";
            Result<UserInternalRegisterResponse> result = exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(req, jsonHeaders()),
                    new ParameterizedTypeReference<Result<UserInternalRegisterResponse>>() {
                    }
            );
            return requireOk(result);
        });
    }

    public int activate(int userId, String activationCode) {
        return call("activate", () -> {
            UserInternalActivateRequest req = new UserInternalActivateRequest();
            req.setActivationCode(activationCode);
            String url = properties.getBaseUrl() + "/internal/users/" + userId + "/activate";
            Result<UserInternalActivationResponse> result = exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(req, jsonHeaders()),
                    new ParameterizedTypeReference<Result<UserInternalActivationResponse>>() {
                    }
            );
            UserInternalActivationResponse resp = requireOk(result);
            return resp == null ? 2 : resp.getResult();
        });
    }

    public UserInternalUserByEmailResponse findByEmailOrNull(String email) {
        return call("byEmail", () -> {
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/internal/users/by-email")
                    .queryParam("email", email)
                    .toUriString();
            Result<UserInternalUserByEmailResponse> result = exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()),
                    new ParameterizedTypeReference<Result<UserInternalUserByEmailResponse>>() {
                    }
            );
            // 约定：不存在时 data=null（仍为 OK）
            return requireOk(result);
        });
    }

    public void updatePassword(int userId, String newPassword) {
        call("updatePassword", () -> {
            UserInternalUpdatePasswordRequest req = new UserInternalUpdatePasswordRequest();
            req.setNewPassword(newPassword);
            String url = properties.getBaseUrl() + "/internal/users/" + userId + "/password";
            Result<Void> result = exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(req, jsonHeaders()),
                    new ParameterizedTypeReference<Result<Void>>() {
                    }
            );
            requireOk(result);
            return null;
        });
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
        headers.set(HEADER_INTERNAL_TOKEN, properties.getInternalToken());
        return headers;
    }

    private <T> T requireOk(Result<T> result) {
        if (result == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "user-service 响应为空");
        }
        if (result.getCode() != CommonErrorCode.OK.getCode()) {
            throw new BusinessException(new SimpleErrorCode(result.getCode(), result.getMessage()), result.getMessage());
        }
        return result.getData();
    }

    private <T> T call(String api, Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            record(api, "success", start);
            return v;
        } catch (BusinessException e) {
            record(api, "biz_error", start);
            throw e;
        } catch (HttpClientErrorException.Forbidden e) {
            record(api, "error", start);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "user-service internal 调用被拒绝（请检查 internal-token 配置）");
        } catch (RestClientException e) {
            record(api, "error", start);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "user-service 不可用");
        } catch (Exception e) {
            record(api, "error", start);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "user-service 调用失败");
        }
    }

    private void record(String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("api", api, "outcome", outcome);
        meterRegistry.counter("auth_user_client_requests_total", tags).increment();
        meterRegistry.timer("auth_user_client_latency", tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private <T> Result<T> exchange(String url, HttpMethod method, HttpEntity<?> entity, ParameterizedTypeReference<Result<T>> typeRef) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("url 不能为空");
        }
        try {
            ResponseEntity<Result<T>> resp = restTemplate.exchange(url, method, entity, typeRef);
            return resp.getBody();
        } catch (RestClientException e) {
            throw e;
        }
    }
}

