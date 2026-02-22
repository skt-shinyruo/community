package com.nowcoder.community.gateway.filter;

import com.nowcoder.community.common.net.TrustedProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForwardedOriginResolverTest {

    @Test
    void shouldUseRequestUriWhenTrustedProxyDisabled() {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setEnabled(false);

        ForwardedOriginResolver resolver = new ForwardedOriginResolver(props);

        MockServerHttpRequest request = MockServerHttpRequest.post("http://localhost/api/auth/login")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "community.example.com")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 12345))
                .build();

        ForwardedOriginResolver.ResolvedOrigin origin = resolver.resolve(request);
        assertThat(origin).isNotNull();
        assertThat(origin.source()).isEqualTo("request");
        assertThat(origin.scheme()).isEqualTo("http");
        assertThat(origin.host()).isEqualTo("localhost");
        assertThat(origin.port()).isEqualTo(80);
    }

    @Test
    void shouldUseXForwardedHeadersWhenTrustedProxyEnabledAndRemoteInAllowlist() {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setEnabled(true);
        props.setCidrs(List.of("10.0.0.0/8"));

        ForwardedOriginResolver resolver = new ForwardedOriginResolver(props);

        MockServerHttpRequest request = MockServerHttpRequest.post("http://localhost/api/auth/login")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "community.example.com")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 12345))
                .build();

        ForwardedOriginResolver.ResolvedOrigin origin = resolver.resolve(request);
        assertThat(origin).isNotNull();
        assertThat(origin.source()).isEqualTo("forwarded");
        assertThat(origin.scheme()).isEqualTo("https");
        assertThat(origin.host()).isEqualTo("community.example.com");
        assertThat(origin.port()).isEqualTo(443);
    }

    @Test
    void shouldParseForwardedHeaderWhenTrustedProxyEnabled() {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setEnabled(true);
        props.setCidrs(List.of("10.0.0.0/8"));

        ForwardedOriginResolver resolver = new ForwardedOriginResolver(props);

        MockServerHttpRequest request = MockServerHttpRequest.post("http://localhost/api/auth/login")
                .header("Forwarded", "for=1.2.3.4;proto=https;host=example.com:8443")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 12345))
                .build();

        ForwardedOriginResolver.ResolvedOrigin origin = resolver.resolve(request);
        assertThat(origin).isNotNull();
        assertThat(origin.source()).isEqualTo("forwarded");
        assertThat(origin.scheme()).isEqualTo("https");
        assertThat(origin.host()).isEqualTo("example.com");
        assertThat(origin.port()).isEqualTo(8443);
    }

    @Test
    void shouldIgnoreForwardedHeadersWhenRemoteNotTrusted() {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setEnabled(true);
        props.setCidrs(List.of("10.0.0.0/8"));

        ForwardedOriginResolver resolver = new ForwardedOriginResolver(props);

        MockServerHttpRequest request = MockServerHttpRequest.post("http://localhost/api/auth/login")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "community.example.com")
                .remoteAddress(new InetSocketAddress("192.168.1.10", 12345))
                .build();

        ForwardedOriginResolver.ResolvedOrigin origin = resolver.resolve(request);
        assertThat(origin).isNotNull();
        assertThat(origin.source()).isEqualTo("request");
        assertThat(origin.scheme()).isEqualTo("http");
        assertThat(origin.host()).isEqualTo("localhost");
        assertThat(origin.port()).isEqualTo(80);
    }
}
