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
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.function.Supplier;

@Service
public class UserServiceInternalClient {

    private static final String METRIC_CLIENT = "auth-service:user-service";

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
            ResponseEntity<Result<UserInternalAuthenticateResponse>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(req, InternalClientSupport.jsonHeaders(properties.getInternalToken(), "user-service")),
                    new ParameterizedTypeReference<Result<UserInternalAuthenticateResponse>>() {
                    }
            );
            return InternalClientSupport.unwrap(resp, "user-service");
        });
    }

    public UserInternalSessionProfileResponse sessionProfile(int userId) {
        return call("sessionProfile", () -> {
            String url = properties.getBaseUrl() + "/internal/users/" + userId + "/session-profile";
            ResponseEntity<Result<UserInternalSessionProfileResponse>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(InternalClientSupport.jsonHeaders(properties.getInternalToken(), "user-service")),
                    new ParameterizedTypeReference<Result<UserInternalSessionProfileResponse>>() {
                    }
            );
            return InternalClientSupport.unwrap(resp, "user-service");
        });
    }

    public UserInternalRegisterResponse register(String username, String password, String email) {
        return call("register", () -> {
            UserInternalRegisterRequest req = new UserInternalRegisterRequest();
            req.setUsername(username);
            req.setPassword(password);
            req.setEmail(email);
            String url = properties.getBaseUrl() + "/internal/users/register";
            ResponseEntity<Result<UserInternalRegisterResponse>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(req, InternalClientSupport.jsonHeaders(properties.getInternalToken(), "user-service")),
                    new ParameterizedTypeReference<Result<UserInternalRegisterResponse>>() {
                    }
            );
            return InternalClientSupport.unwrap(resp, "user-service");
        });
    }

    public int activate(int userId, String activationCode) {
        return call("activate", () -> {
            UserInternalActivateRequest req = new UserInternalActivateRequest();
            req.setActivationCode(activationCode);
            String url = properties.getBaseUrl() + "/internal/users/" + userId + "/activate";
            ResponseEntity<Result<UserInternalActivationResponse>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(req, InternalClientSupport.jsonHeaders(properties.getInternalToken(), "user-service")),
                    new ParameterizedTypeReference<Result<UserInternalActivationResponse>>() {
                    }
            );
            UserInternalActivationResponse data = InternalClientSupport.unwrap(resp, "user-service");
            return data == null ? 2 : data.getResult();
        });
    }

    public UserInternalUserByEmailResponse findByEmailOrNull(String email) {
        return call("byEmail", () -> {
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getBaseUrl() + "/internal/users/by-email")
                    .queryParam("email", email)
                    .toUriString();
            ResponseEntity<Result<UserInternalUserByEmailResponse>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(InternalClientSupport.jsonHeaders(properties.getInternalToken(), "user-service")),
                    new ParameterizedTypeReference<Result<UserInternalUserByEmailResponse>>() {
                    }
            );
            // 约定：不存在时 data=null（仍为 OK）
            return InternalClientSupport.unwrap(resp, "user-service");
        });
    }

    public void updatePassword(int userId, String newPassword) {
        call("updatePassword", () -> {
            if (!StringUtils.hasText(properties.getOpsInternalToken())) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "auth.user-client.ops-internal-token 未配置");
            }
            UserInternalUpdatePasswordRequest req = new UserInternalUpdatePasswordRequest();
            req.setNewPassword(newPassword);
            String url = properties.getBaseUrl() + "/internal/users/" + userId + "/password";
            ResponseEntity<Result<Void>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(req, InternalClientSupport.jsonHeaders(properties.getOpsInternalToken(), "user-service")),
                    new ParameterizedTypeReference<Result<Void>>() {
                    }
            );
            InternalClientSupport.unwrap(resp, "user-service");
            return null;
        });
    }

    private <T> T call(String api, Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, InternalClientSupport.OUTCOME_SUCCESS, start);
            return v;
        } catch (BusinessException e) {
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, "biz_error", start);
            throw e;
        } catch (RestClientException e) {
            String outcome = InternalClientSupport.isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, outcome, start);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "user-service 不可用");
        } catch (RuntimeException e) {
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, InternalClientSupport.OUTCOME_ERROR, start);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "user-service 调用失败");
        }
    }
}
