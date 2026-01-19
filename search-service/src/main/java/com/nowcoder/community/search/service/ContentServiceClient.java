package com.nowcoder.community.search.service;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.search.api.dto.ContentPostScanResponse;
import com.nowcoder.community.search.config.ContentServiceClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.util.concurrent.TimeUnit;

/**
 * search-service -> content-service 内部调用（用于重建索引）。
 */
@Service
public class ContentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ContentServiceClient.class);

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final ContentServiceClientProperties properties;

    public ContentServiceClient(RestTemplate restTemplate, MeterRegistry meterRegistry, ContentServiceClientProperties properties) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public ContentPostScanResponse scanPosts(int afterId, int limit) {
        if (!StringUtils.hasText(properties.getInternalToken())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "content-service internal-token 未配置");
        }

        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "content-service base-url 未配置");
        }

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/internal/content/posts")
                .queryParam("afterId", Math.max(0, afterId))
                .queryParam("limit", Math.min(1000, Math.max(1, limit)))
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", properties.getInternalToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        long start = System.nanoTime();
        try {
            ResponseEntity<Result<ContentPostScanResponse>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Result<ContentPostScanResponse>>() {
                    }
            );
            ContentPostScanResponse data = resp.getBody() == null ? null : resp.getBody().getData();
            record("scanPosts", "success", start);
            return data;
        } catch (RestClientException e) {
            record("scanPosts", "error", start);
            log.warn("[content-client] call failed: {}", e.toString());
            throw e;
        }
    }

    private void record(String api, String outcome, long startNanos) {
        Tags tags = Tags.of("api", api, "outcome", outcome);
        meterRegistry.counter("search_content_client_requests_total", tags).increment();
        meterRegistry.timer("search_content_client_latency", tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
}
