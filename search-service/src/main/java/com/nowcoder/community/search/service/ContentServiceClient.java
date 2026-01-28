package com.nowcoder.community.search.service;

// content-service 内部调用客户端：用于 reindex 扫描帖子数据。
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import com.nowcoder.community.search.api.dto.ContentPostScanResponse;
import com.nowcoder.community.search.config.ContentServiceClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
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

/**
 * search-service -> content-service 内部调用（用于重建索引）。
 */
@Service
public class ContentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ContentServiceClient.class);
    private static final String SERVICE_NAME = "content-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final ContentServiceClientProperties properties;

    public ContentServiceClient(RestTemplate restTemplate, MeterRegistry meterRegistry, ContentServiceClientProperties properties) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public ContentPostScanResponse scanPosts(int afterId, int limit) {
        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT, "content-service base-url 未配置");
        }

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/internal/content/posts")
                .queryParam("afterId", Math.max(0, afterId))
                .queryParam("limit", Math.min(1000, Math.max(1, limit)))
                .toUriString();

        HttpHeaders headers = InternalClientSupport.jsonHeaders(properties.getInternalToken(), SERVICE_NAME);
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
            ContentPostScanResponse data = InternalClientSupport.unwrap(resp.getBody(), SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanPosts", "success", start);
            return data;
        } catch (BusinessException e) {
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanPosts", "error", start);
            throw e;
        } catch (RestClientException e) {
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanPosts", "error", start);
            log.warn("[content-client] call failed: {}", e.toString());
            throw new BusinessException(com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE, "content-service 不可用");
        }
    }
}
