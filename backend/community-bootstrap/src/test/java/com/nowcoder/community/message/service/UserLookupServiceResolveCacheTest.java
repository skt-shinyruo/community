package com.nowcoder.community.message.service;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.user.api.internal.UserReadApi;
import com.nowcoder.community.user.api.internal.dto.UserSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserLookupServiceResolveCacheTest {

    @Test
    void resolveByUsernameShouldUseShortTtlCache() throws Exception {
        UserReadApi userReadApi = mock(UserReadApi.class);
        UserSummary u = new UserSummary();
        u.setId(123);
        u.setUsername("alice");
        u.setHeaderUrl("h");
        when(userReadApi.resolveByUsernameOrNull("alice")).thenReturn(Result.ok(u));

        UserLookupService service = new UserLookupService(
                new SimpleMeterRegistry(),
                false,
                Duration.ofSeconds(60),
                10,
                userReadApi
        );

        Integer id1 = service.safeResolveUserIdByUsername("alice ");
        Integer id2 = service.safeResolveUserIdByUsername("alice");

        assertThat(id1).isEqualTo(123);
        assertThat(id2).isEqualTo(123);

        verify(userReadApi, times(1)).resolveByUsernameOrNull("alice");
    }
}
