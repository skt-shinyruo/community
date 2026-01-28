package com.nowcoder.community.user.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import com.nowcoder.community.user.config.SocialServiceClientProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialServiceClientTest {

    @Test
    void safeMethodsShouldDegradeAndEmitMetricsWhenDownstreamFails() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SocialServiceClientProperties properties = new SocialServiceClientProperties();
        properties.setBaseUrl("http://social-service");
        properties.setInternalToken("t");
        properties.setFailOpen(true);
        SocialServiceClient client = new SocialServiceClient(restTemplate, registry, properties);

        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("downstream error"));

        assertThat(client.safeUserLikeCount(1)).isEqualTo(0);
        assertThat(client.safeFolloweeCount(1)).isEqualTo(0);
        assertThat(client.safeFollowerCount(1)).isEqualTo(0);
        assertThat(client.safeHasFollowed(1, 2)).isFalse();

        assertThat(registry.find("user_social_client_requests_total")
                .tags("api", "userLikeCount", "outcome", "degraded")
                .counter()).isNotNull();
        assertThat(registry.find("user_social_client_requests_total")
                .tags("api", "userLikeCount", "outcome", "degraded")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
