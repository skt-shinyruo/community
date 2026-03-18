package com.nowcoder.community.auth.service;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.user.api.internal.UserAuthApi;
import com.nowcoder.community.user.api.internal.dto.UserInternalActivationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAuthAccessTest {

    @Test
    void activateShouldReturnDownstreamResult() {
        UserAuthApi userAuthApi = mock(UserAuthApi.class);
        UserInternalActivationResponse activation = new UserInternalActivationResponse();
        activation.setResult(1);
        when(userAuthApi.activate(123, "code")).thenReturn(Result.ok(activation));

        UserAuthAccess access = new UserAuthAccess(new SimpleMeterRegistry(), userAuthApi);

        int r = access.activate(123, "code");
        assertThat(r).isEqualTo(1);
    }

    @Test
    void activateShouldReturn2WhenDownstreamDataIsNull() {
        UserAuthApi userAuthApi = mock(UserAuthApi.class);
        when(userAuthApi.activate(123, "code")).thenReturn(Result.ok(null));

        UserAuthAccess access = new UserAuthAccess(new SimpleMeterRegistry(), userAuthApi);

        int r = access.activate(123, "code");
        assertThat(r).isEqualTo(2);
    }
}
