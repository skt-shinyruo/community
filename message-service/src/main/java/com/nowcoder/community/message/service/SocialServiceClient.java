package com.nowcoder.community.message.service;

// social-service 内部拉黑关系查询客户端：用于“私信发送”写路径校验。
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * 约定：任意一方拉黑另一方 -> 禁止发送私信。
 */
@Service
public class SocialServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SocialServiceClient.class);
    private static final String SERVICE_NAME = "social-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final String internalToken;
    private final boolean failOpen;

    public SocialServiceClient(
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
            throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法发送私信");
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
            Result<Boolean> result = exchange(url, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<Result<Boolean>>() {
            });
            Boolean data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data;
        } catch (RuntimeException e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[social-client] degraded (api=isEitherBlocked): {}", e.toString());
                return false;
            }
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", InternalClientSupport.OUTCOME_ERROR, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "social-service 不可用");
        }
    }

    private <T> Result<T> exchange(String url, HttpMethod method, HttpEntity<?> entity, ParameterizedTypeReference<Result<T>> typeRef) {
        try {
            ResponseEntity<Result<T>> resp = restTemplate.exchange(url, method, entity, typeRef);
            return resp.getBody();
        } catch (RestClientException e) {
            throw new BusinessException(SERVICE_UNAVAILABLE, "social-service 不可用");
        }
    }
}
