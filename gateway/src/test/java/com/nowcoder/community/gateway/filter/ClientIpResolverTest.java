package com.nowcoder.community.gateway.filter;

// 可信代理 IP 解析测试：确保未信任时忽略 XFF，可信代理时采用 XFF。
import com.nowcoder.community.platform.net.TrustedProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void shouldIgnoreXffWhenDisabled() {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setEnabled(false);
        props.setCidrs(List.of("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 1234))
                .header("X-Forwarded-For", "203.0.113.10")
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("10.1.2.3");
    }

    @Test
    void shouldIgnoreXffWhenRemoteNotTrusted() {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setEnabled(true);
        props.setCidrs(List.of("192.168.0.0/16"));
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 1234))
                .header("X-Forwarded-For", "203.0.113.10")
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("10.1.2.3");
    }

    @Test
    void shouldUseXffWhenRemoteTrusted() {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setEnabled(true);
        props.setCidrs(List.of("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 1234))
                .header("X-Forwarded-For", "203.0.113.10, 10.1.2.3")
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }
}
