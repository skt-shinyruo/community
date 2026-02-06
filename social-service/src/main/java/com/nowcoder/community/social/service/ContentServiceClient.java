package com.nowcoder.community.social.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.internal.dto.EntityResolveResponse;
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
 * content-service internal client：解析 POST/COMMENT 的 owner/postId，避免信任客户端注入字段。
 */
@Service
public class ContentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ContentServiceClient.class);
    private static final String SERVICE_NAME = "content-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final String internalToken;
    private final boolean failOpen;

    public ContentServiceClient(
            RestTemplate restTemplate,
            MeterRegistry meterRegistry,
            @Value("${clients.content.base-url:http://content-service}") String baseUrl,
            @Value("${clients.content.internal-token:}") String internalToken,
            @Value("${clients.content.fail-open:false}") boolean failOpen
    ) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.failOpen = failOpen;
    }

    public EntityResolveResponse resolveEntity(int entityType, int entityId) {
        if (entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(INVALID_ARGUMENT, "content-service base-url 未配置");
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/content/entities/resolve")
                .queryParam("entityType", entityType)
                .queryParam("entityId", entityId)
                .toUriString();

        HttpHeaders headers = InternalClientSupport.jsonHeaders(internalToken, SERVICE_NAME);
        long start = System.nanoTime();
        try {
            ResponseEntity<Result<EntityResolveResponse>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Result<EntityResolveResponse>>() {
                    }
            );
            EntityResolveResponse data = InternalClientSupport.unwrap(resp, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "resolveEntity", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data;
        } catch (RuntimeException e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "resolveEntity", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[content-client] degraded (api=resolveEntity): {}", e.toString());
            } else {
                String outcome = InternalClientSupport.isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "resolveEntity", outcome, start);
            }
            if (e instanceof BusinessException be) {
                throw be;
            }
            if (e instanceof RestClientException) {
                throw new BusinessException(SERVICE_UNAVAILABLE, "content-service 不可用");
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "content-service 不可用");
        }
    }
}
