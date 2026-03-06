package com.nowcoder.community.message.service;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.user.api.rpc.UserReadRpcService;
import com.nowcoder.community.user.api.rpc.dto.UserSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceClientResolveCacheTest {

    @Test
    void resolveByUsernameShouldUseShortTtlCache() throws Exception {
        UserReadRpcService rpc = mock(UserReadRpcService.class);
        UserSummary u = new UserSummary();
        u.setId(123);
        u.setUsername("alice");
        u.setHeaderUrl("h");
        when(rpc.resolveByUsernameOrNull("alice")).thenReturn(Result.ok(u));

        UserServiceClient client = new UserServiceClient(
                new SimpleMeterRegistry(),
                false,
                Duration.ofSeconds(60),
                10,
                rpc
        );

        Integer id1 = client.safeResolveUserIdByUsername("alice ");
        Integer id2 = client.safeResolveUserIdByUsername("alice");

        assertThat(id1).isEqualTo(123);
        assertThat(id2).isEqualTo(123);

        verify(rpc, times(1)).resolveByUsernameOrNull("alice");
    }
}
