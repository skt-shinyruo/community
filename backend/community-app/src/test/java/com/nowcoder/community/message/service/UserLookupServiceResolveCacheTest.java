package com.nowcoder.community.message.service;

import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.InternalUserService;
import com.nowcoder.community.user.service.UserService;
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
        UserService userService = mock(UserService.class);
        InternalUserService internalUserService = mock(InternalUserService.class);
        User user = new User();
        user.setId(123);
        user.setUsername("alice");
        user.setHeaderUrl("h");
        when(userService.getByUsername("alice")).thenReturn(user);

        UserLookupService service = new UserLookupService(
                new SimpleMeterRegistry(),
                false,
                Duration.ofSeconds(60),
                10,
                userService,
                internalUserService
        );

        Integer id1 = service.safeResolveUserIdByUsername("alice ");
        Integer id2 = service.safeResolveUserIdByUsername("alice");

        assertThat(id1).isEqualTo(123);
        assertThat(id2).isEqualTo(123);

        verify(userService, times(1)).getByUsername("alice");
    }
}
