package com.nowcoder.community.content.like;

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

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * social-service internal likes 扫描客户端：用于回填 Redis 点赞投影（减少冷启动窗口）。
 */
@Service
public class SocialLikeScanClient {

    private static final Logger log = LoggerFactory.getLogger(SocialLikeScanClient.class);
    private static final String SERVICE_NAME = "social-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final boolean failOpen;

    public SocialLikeScanClient(
            RestTemplate restTemplate,
            MeterRegistry meterRegistry,
            @Value("${clients.social.base-url:http://social-service}") String baseUrl,
            @Value("${clients.social.fail-open:false}") boolean failOpen
    ) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.baseUrl = baseUrl;
        this.failOpen = failOpen;
    }

    public SocialLikeScanResponse scan(int entityType, long afterEntityId, long afterUserId, int limit) {
        if (entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "social-service base-url 未配置");
        }
        long ae = Math.max(0L, afterEntityId);
        long au = Math.max(0L, afterUserId);
        int l = Math.min(2000, Math.max(1, limit));

        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/social/likes/scan")
                .queryParam("entityType", entityType)
                .queryParam("afterEntityId", ae)
                .queryParam("afterUserId", au)
                .queryParam("limit", l)
                .toUriString();

        HttpHeaders headers = InternalClientSupport.jsonHeaders();
        long start = System.nanoTime();
        try {
            ResponseEntity<Result<SocialLikeScanResponse>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Result<SocialLikeScanResponse>>() {
                    }
            );
            SocialLikeScanResponse data = InternalClientSupport.unwrap(resp, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanLikes", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data == null ? new SocialLikeScanResponse() : data;
        } catch (RuntimeException e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanLikes", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[social-like-scan] degraded: {}", e.toString());
                return new SocialLikeScanResponse();
            }
            String outcome = InternalClientSupport.isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanLikes", outcome, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            if (e instanceof RestClientException) {
                throw new BusinessException(SERVICE_UNAVAILABLE, "social-service 不可用");
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "social-service 不可用");
        }
    }
}
