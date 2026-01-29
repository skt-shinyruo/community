package com.nowcoder.community.message.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
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

import java.time.Instant;
import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * user-service 内部治理接口客户端：用于处罚状态查询与批量扫描（投影回填/纠偏）。
 */
@Service
public class UserModerationClient {

    private static final Logger log = LoggerFactory.getLogger(UserModerationClient.class);
    private static final String SERVICE_NAME = "user-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final String internalToken;
    private final boolean failOpen;

    public UserModerationClient(
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

    public ModerationStatus getStatus(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "user-service base-url 未配置");
        }
        String url = baseUrl + "/internal/users/" + userId + "/moderation-status";
        long start = System.nanoTime();
        try {
            ResponseEntity<Result<ModerationStatus>> resp = exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(InternalClientSupport.jsonHeaders(internalToken, SERVICE_NAME)),
                    new ParameterizedTypeReference<Result<ModerationStatus>>() {
                    }
            );
            ModerationStatus data = InternalClientSupport.unwrap(resp, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data;
        } catch (Exception e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[user-client] degraded (api=getStatus): {}", e.toString());
                ModerationStatus status = new ModerationStatus();
                status.setUserId(userId);
                return status;
            }
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", InternalClientSupport.OUTCOME_ERROR, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    public List<ModerationStatus> scanStatuses(int afterId, int limit) {
        int a = Math.max(0, afterId);
        int l = Math.min(500, Math.max(1, limit));
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "user-service base-url 未配置");
        }
        String url = baseUrl + "/internal/users/moderation-scan?afterId=" + a + "&limit=" + l;
        long start = System.nanoTime();
        try {
            ResponseEntity<Result<List<ModerationStatus>>> resp = exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(InternalClientSupport.jsonHeaders(internalToken, SERVICE_NAME)),
                    new ParameterizedTypeReference<Result<List<ModerationStatus>>>() {
                    }
            );
            List<ModerationStatus> data = InternalClientSupport.unwrap(resp, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data == null ? List.of() : data;
        } catch (Exception e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[user-client] degraded (api=scanStatuses): {}", e.toString());
                return List.of();
            }
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", InternalClientSupport.OUTCOME_ERROR, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    private <T> ResponseEntity<Result<T>> exchange(
            String url,
            HttpMethod method,
            HttpEntity<?> entity,
            ParameterizedTypeReference<Result<T>> typeRef
    ) {
        try {
            return restTemplate.exchange(url, method, entity, typeRef);
        } catch (RestClientException e) {
            throw e;
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
}
