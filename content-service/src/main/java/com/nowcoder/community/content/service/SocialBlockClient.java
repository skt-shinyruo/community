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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.SocketTimeoutException;

import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

// social-service 内部拉黑关系查询客户端：用于写路径校验（评论/私信等）。

@Service
public class SocialBlockClient {

    private static final Logger log = LoggerFactory.getLogger(SocialBlockClient.class);
    private static final String SERVICE_NAME = "social-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final String internalToken;
    private final boolean failOpen;

    public SocialBlockClient(
            RestTemplate restTemplate,
            MeterRegistry meterRegistry,
            @Value("${clients.social.base-url:http://social-service}") String baseUrl,
            @Value("${clients.social.internal-token:}") String internalToken,
            @Value("${clients.social.fail-open:false}") boolean failOpen
    ) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.failOpen = failOpen;
    }

    public void assertNotBlocked(int userIdA, int userIdB) {
        if (userIdA <= 0 || userIdB <= 0 || userIdA == userIdB) {
            return;
        }
        Boolean blocked = isEitherBlocked(userIdA, userIdB);
        if (Boolean.TRUE.equals(blocked)) {
            throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
        }
    }

    public Boolean isEitherBlocked(int userIdA, int userIdB) {
        if (userIdA <= 0 || userIdB <= 0 || userIdA == userIdB) {
            return false;
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "social-service base-url 未配置");
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/social/blocks/relation")
                .queryParam("userIdA", userIdA)
                .queryParam("userIdB", userIdB)
                .toUriString();

        HttpHeaders headers = InternalClientSupport.jsonHeaders(internalToken, SERVICE_NAME);
        long start = System.nanoTime();
        try {
            ResponseEntity<Result<Boolean>> resp = exchange(url, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<Result<Boolean>>() {
            });
            Boolean data = InternalClientSupport.unwrap(resp, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data;
        } catch (RuntimeException e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[social-client] degraded (api=isEitherBlocked): {}", e.toString());
                return false;
            }
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", classifyOutcome(e), start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "social-service 不可用");
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
        try {
            return restTemplate.exchange(url, method, entity, typeRef);
        } catch (RestClientException e) {
            throw e;
        }
    }
}
