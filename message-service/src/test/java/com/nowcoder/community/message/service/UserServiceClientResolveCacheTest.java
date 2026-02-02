package com.nowcoder.community.message.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.message.service.dto.UserSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceClientResolveCacheTest {

    @Test
    void resolveByUsernameShouldUseShortTtlCache() {
        RestTemplate restTemplate = mock(RestTemplate.class);

        UserSummary u = new UserSummary();
        u.setId(123);
        u.setUsername("alice");
        u.setHeaderUrl("h");

        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(Result.ok(u)));

        UserServiceClient client = new UserServiceClient(
                restTemplate,
                new SimpleMeterRegistry(),
                "http://user-service",
                "t",
                false,
                Duration.ofSeconds(60),
                10
        );

        Integer id1 = client.safeResolveUserIdByUsername("alice ");
        Integer id2 = client.safeResolveUserIdByUsername("alice");

        assertThat(id1).isEqualTo(123);
        assertThat(id2).isEqualTo(123);

        verify(restTemplate, times(1)).exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }
}

