package com.nowcoder.community.auth.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.user.api.rpc.UserInternalRpcService;
import com.nowcoder.community.user.api.rpc.dto.UserInternalActivationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceInternalClientTest {

    @Test
    void activateShouldReturnDownstreamResult() {
        UserInternalRpcService rpc = mock(UserInternalRpcService.class);
        UserInternalActivationResponse activation = new UserInternalActivationResponse();
        activation.setResult(1);
        when(rpc.activate(123, "code")).thenReturn(Result.ok(activation));

        UserServiceInternalClient client = new UserServiceInternalClient(new SimpleMeterRegistry());
        TestSupport.injectRpc(client, rpc);

        int r = client.activate(123, "code");
        assertThat(r).isEqualTo(1);
    }

    @Test
    void activateShouldReturn2WhenDownstreamDataIsNull() {
        UserInternalRpcService rpc = mock(UserInternalRpcService.class);
        when(rpc.activate(123, "code")).thenReturn(Result.ok(null));

        UserServiceInternalClient client = new UserServiceInternalClient(new SimpleMeterRegistry());
        TestSupport.injectRpc(client, rpc);

        int r = client.activate(123, "code");
        assertThat(r).isEqualTo(2);
    }

    private static final class TestSupport {
        private static void injectRpc(UserServiceInternalClient client, UserInternalRpcService rpc) {
            try {
                var f = UserServiceInternalClient.class.getDeclaredField("userInternalRpcService");
                f.setAccessible(true);
                f.set(client, rpc);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
