package com.nowcoder.community.content.service;

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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

// user-service 内部治理接口客户端：用于禁言/封禁与状态查询（开发阶段默认放行；生产建议通过网络隔离/网关策略收敛暴露面）。

@Service
public class UserModerationClient {

    private static final Logger log = LoggerFactory.getLogger(UserModerationClient.class);
    private static final String SERVICE_NAME = "user-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final boolean failOpen;

    public UserModerationClient(
            RestTemplate restTemplate,
            MeterRegistry meterRegistry,
            @Value("${clients.user.base-url:http://user-service}") String baseUrl,
            @Value("${clients.user.fail-open:false}") boolean failOpen
    ) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.baseUrl = baseUrl;
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
            ResponseEntity<Result<ModerationStatus>> resp = exchange(url, HttpMethod.GET, new HttpEntity<>(InternalClientSupport.jsonHeaders()), new ParameterizedTypeReference<Result<ModerationStatus>>() {
            });
            ModerationStatus data = InternalClientSupport.unwrap(resp, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data;
        } catch (RuntimeException e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[user-client] degraded (api=getStatus): {}", e.toString());
                ModerationStatus status = new ModerationStatus();
                status.setUserId(userId);
                status.setMuteUntil(null);
                status.setBanUntil(null);
                return status;
            }
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", classifyOutcome(e), start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    /**
     * internal 投影回填/纠偏：按主键游标批量扫描用户处罚状态。
     *
     * <p>用途：content-service 本地投影冷启动基线构建，避免投影缺失导致写路径不可用。</p>
     */
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
                    new HttpEntity<>(InternalClientSupport.jsonHeaders()),
                    new ParameterizedTypeReference<Result<List<ModerationStatus>>>() {
                    }
            );
            List<ModerationStatus> data = InternalClientSupport.unwrap(resp, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data == null ? List.of() : data;
        } catch (RuntimeException e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[user-client] degraded (api=scanStatuses): {}", e.toString());
                return List.of();
            }
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", classifyOutcome(e), start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
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
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "user-service base-url 未配置");
        }
        int seconds = Math.max(0, durationSeconds);

        ModerationApplyRequest req = new ModerationApplyRequest();
        req.setAction(action);
        req.setDurationSeconds(seconds);

        String url = baseUrl + "/internal/users/" + userId + "/moderation";
        long start = System.nanoTime();
        try {
            ResponseEntity<Result<ModerationStatus>> resp = exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(req, InternalClientSupport.jsonHeaders()),
                    new ParameterizedTypeReference<Result<ModerationStatus>>() {
                    }
            );
            InternalClientSupport.unwrap(resp, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "apply:" + action, InternalClientSupport.OUTCOME_SUCCESS, start);
        } catch (RuntimeException e) {
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "apply:" + action, classifyOutcome(e), start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    private String classifyOutcome(Throwable t) {
        if (isTimeout(t)) {
            return InternalClientSupport.OUTCOME_TIMEOUT;
        }
        return InternalClientSupport.OUTCOME_ERROR;
    }

    private boolean isTimeout(Throwable t) {
        if (t instanceof ResourceAccessException) {
            return true;
        }
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private <T> ResponseEntity<Result<T>> exchange(String url, HttpMethod method, HttpEntity<?> entity, ParameterizedTypeReference<Result<T>> typeRef) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(INVALID_ARGUMENT, "url 不能为空");
        }
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
