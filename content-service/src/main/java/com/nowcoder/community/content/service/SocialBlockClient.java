// social-service 内部拉黑关系查询客户端：用于写路径校验（评论/私信等）。
package com.nowcoder.community.content.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

@Service
public class SocialBlockClient {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;

    public SocialBlockClient(
            RestTemplate restTemplate,
            @Value("${clients.social.base-url:http://social-service}") String baseUrl,
            @Value("${clients.social.internal-token:${INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
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
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(FORBIDDEN, "internal-token 未配置（请设置 env: INTERNAL_TOKEN）");
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/social/blocks/relation")
                .queryParam("userIdA", userIdA)
                .queryParam("userIdB", userIdB)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_INTERNAL_TOKEN, internalToken);

        Result<Boolean> result = exchange(url, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<Result<Boolean>>() {
        });
        if (result == null) {
            throw new BusinessException(SERVICE_UNAVAILABLE, "social-service 响应为空");
        }
        if (result.getCode() != 0) {
            throw new BusinessException(INVALID_ARGUMENT, "social-service 返回错误：" + result.getMessage());
        }
        return result.getData();
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

