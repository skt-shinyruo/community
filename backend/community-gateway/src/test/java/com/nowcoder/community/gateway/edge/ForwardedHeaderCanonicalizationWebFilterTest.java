package com.nowcoder.community.gateway.edge;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForwardedHeaderCanonicalizationWebFilterTest {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String X_FORWARDED_PORT = "X-Forwarded-Port";
    private static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";
    private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String FORWARDED = "Forwarded";

    @Test
    void propertiesShouldDefaultToDisabledAndDefensivelyHandleNullCidrs() {
        EdgeTrustedProxyProperties properties = new EdgeTrustedProxyProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getCidrs()).isEmpty();

        properties.setCidrs(null);

        assertThat(properties.getCidrs()).isEmpty();
    }

    @Test
    void shouldRejectInvalidTrustedCidrWhenEnabledFilterIsConstructed() {
        EdgeTrustedProxyProperties properties = trusted("not-a-cidr");

        assertThatThrownBy(() -> new ForwardedHeaderCanonicalizationWebFilter(properties, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted CIDR");
    }

    @Test
    void shouldIgnoreInvalidCidrsWhileTrustedProxyModeIsDisabled() {
        EdgeTrustedProxyProperties properties = new EdgeTrustedProxyProperties();
        properties.setCidrs(List.of("not-a-cidr"));

        ForwardedHeaderCanonicalizationWebFilter filter =
                new ForwardedHeaderCanonicalizationWebFilter(properties, null);

        CapturedRequest capture = filter(filter, request("/", resolved("192.0.2.10"))
                .header(X_FORWARDED_FOR, "198.51.100.1")
                .build());

        assertCanonical(capture, "192.0.2.10");
    }

    @Test
    void shouldIgnoreForwardingDataWhenDisabledAndCanonicalizeSocketPeer() {
        EdgeTrustedProxyProperties properties = new EdgeTrustedProxyProperties();
        ForwardedHeaderCanonicalizationWebFilter filter =
                new ForwardedHeaderCanonicalizationWebFilter(properties, null);

        CapturedRequest capture = filter(filter, request("/", resolved("192.0.2.10"))
                .header(FORWARDED, "for=198.51.100.1")
                .header(X_FORWARDED_FOR, "198.51.100.1")
                .header(X_REAL_IP, "198.51.100.2")
                .build());

        assertCanonical(capture, "192.0.2.10");
        assertForwardingHeadersRemoved(capture.downstream());
    }

    @Test
    void shouldIgnoreForwardingDataWhenEnabledWithoutTrustedCidrs() {
        EdgeTrustedProxyProperties properties = new EdgeTrustedProxyProperties();
        properties.setEnabled(true);
        ForwardedHeaderCanonicalizationWebFilter filter =
                new ForwardedHeaderCanonicalizationWebFilter(properties, null);

        CapturedRequest capture = filter(filter, request("/", resolved("192.0.2.11"))
                .header(X_FORWARDED_FOR, "198.51.100.1")
                .build());

        assertCanonical(capture, "192.0.2.11");
    }

    @Test
    void shouldIgnoreForwardingDataFromUntrustedDirectPeer() {
        ForwardedHeaderCanonicalizationWebFilter filter = filterWithTrustedCidrs("10.0.0.0/8");

        CapturedRequest capture = filter(filter, request("/", resolved("192.0.2.10"))
                .header(X_FORWARDED_FOR, "198.51.100.1")
                .build());

        assertCanonical(capture, "192.0.2.10");
    }

    @Test
    void shouldResolveSingleForwardedHopFromTrustedDirectPeer() {
        ForwardedHeaderCanonicalizationWebFilter filter = filterWithTrustedCidrs("10.0.0.0/8");

        CapturedRequest capture = filter(filter, request("/", resolved("10.0.0.5"))
                .header(X_FORWARDED_FOR, "198.51.100.1")
                .build());

        assertCanonical(capture, "198.51.100.1");
    }

    @Test
    void shouldResolveMultiHopChainRightToLeftAcrossHeaderLines() {
        ForwardedHeaderCanonicalizationWebFilter filter = filterWithTrustedCidrs("10.0.0.0/8");

        CapturedRequest capture = filter(filter, request("/", resolved("10.0.0.5"))
                .header(X_FORWARDED_FOR, "198.51.100.1", "10.0.0.8, 10.0.0.9")
                .build());

        assertCanonical(capture, "198.51.100.1");
    }

    @Test
    void shouldIgnoreSpoofedLeftPrefixBeforeFirstUntrustedHop() {
        ForwardedHeaderCanonicalizationWebFilter filter = filterWithTrustedCidrs("10.0.0.0/8");

        CapturedRequest capture = filter(filter, request("/", resolved("10.0.0.5"))
                .header(X_FORWARDED_FOR, "203.0.113.99, 198.51.100.1, 10.0.0.8")
                .build());

        assertCanonical(capture, "198.51.100.1");
    }

    @Test
    void shouldFallBackToSocketPeerForMalformedOrEmptyChainItems() {
        ForwardedHeaderCanonicalizationWebFilter filter = filterWithTrustedCidrs("10.0.0.0/8");

        CapturedRequest malformed = filter(filter, request("/", resolved("10.0.0.5"))
                .header(X_FORWARDED_FOR, "198.51.100.1,not-an-ip")
                .build());
        CapturedRequest empty = filter(filter, request("/", resolved("10.0.0.5"))
                .header(X_FORWARDED_FOR, "198.51.100.1,,10.0.0.8")
                .build());

        assertCanonical(malformed, "10.0.0.5");
        assertCanonical(empty, "10.0.0.5");
    }

    @Test
    void shouldBoundForwardedAdaptationAtThirtyThreeHops() {
        ForwardedHeaderCanonicalizationWebFilter filter = filterWithTrustedCidrs("10.0.0.0/8");
        List<String> hops = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            hops.add("10.0.0.8");
        }

        CapturedRequest capture = filter(filter, request("/", resolved("10.0.0.5"))
                .header(X_FORWARDED_FOR, String.join(",", hops))
                .build());

        assertCanonical(capture, "10.0.0.5");
    }

    @ParameterizedTest
    @MethodSource("normalizedAddressCases")
    void shouldNormalizeIpv4Ipv6AndMappedAddresses(String remote, String forwarded, String expected) {
        ForwardedHeaderCanonicalizationWebFilter filter = forwarded == null
                ? new ForwardedHeaderCanonicalizationWebFilter(new EdgeTrustedProxyProperties(), null)
                : filterWithTrustedCidrs("10.0.0.0/8");
        MockServerHttpRequest.BaseBuilder<?> request = request("/", unresolved(remote));
        if (forwarded != null) {
            request.header(X_FORWARDED_FOR, forwarded);
        }

        CapturedRequest capture = filter(filter, request.build());

        assertCanonical(capture, expected);
    }

    private static Stream<Arguments> normalizedAddressCases() {
        return Stream.of(
                Arguments.of("192.0.2.10", null, "192.0.2.10"),
                Arguments.of("2001:0DB8:0:0:0:0:0:1", null, "2001:db8::1"),
                Arguments.of("::ffff:192.0.2.10", null, "192.0.2.10"),
                Arguments.of("10.0.0.5", "2001:0DB8:0:0:0:0:0:2", "2001:db8::2"),
                Arguments.of("10.0.0.5", "::ffff:198.51.100.4", "198.51.100.4")
        );
    }

    @Test
    void shouldRemoveAllForwardingHeaderValuesAndSetExactlyOneCanonicalXff() {
        ForwardedHeaderCanonicalizationWebFilter filter = filterWithTrustedCidrs("10.0.0.0/8");

        CapturedRequest capture = filter(filter, request("/", resolved("10.0.0.5"))
                .header(FORWARDED, "for=203.0.113.9", "for=198.51.100.2")
                .header(X_FORWARDED_FOR, "198.51.100.1", "10.0.0.8")
                .header(X_FORWARDED_HOST, "attacker.example", "internal.example")
                .header(X_FORWARDED_PORT, "444", "443")
                .header(X_FORWARDED_PREFIX, "/attacker", "/internal")
                .header(X_FORWARDED_PROTO, "http", "https")
                .header(X_REAL_IP, "203.0.113.10", "203.0.113.11")
                .build());

        assertCanonical(capture, "198.51.100.1");
        assertForwardingHeadersRemoved(capture.downstream());
        assertThat(capture.downstream().getRequest().getHeaders().get(X_FORWARDED_FOR))
                .containsExactly("198.51.100.1");
    }

    @Test
    void shouldRemoveForwardingHeadersWithoutAttributeForNullOrInvalidRemote() {
        ForwardedHeaderCanonicalizationWebFilter filter = filterWithTrustedCidrs("10.0.0.0/8");

        CapturedRequest nullRemote = filter(filter, MockServerHttpRequest.get("/")
                .header(FORWARDED, "for=198.51.100.1")
                .header(X_FORWARDED_FOR, "198.51.100.1")
                .header(X_REAL_IP, "198.51.100.1")
                .build());
        CapturedRequest invalidRemote = filter(filter, request("/", unresolved("localhost.example"))
                .header(X_FORWARDED_FOR, "198.51.100.1")
                .build());

        assertNoCanonicalClient(nullRemote);
        assertNoCanonicalClient(invalidRemote);
    }

    @Test
    void shouldIncrementLowCardinalityMalformedAndTruncatedMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ForwardedHeaderCanonicalizationWebFilter filter =
                new ForwardedHeaderCanonicalizationWebFilter(trusted("10.0.0.0/8"), registry);

        filter(filter, request("/", resolved("10.0.0.5"))
                .header(X_FORWARDED_FOR, "198.51.100.1,,10.0.0.8")
                .build());
        filter(filter, request("/", resolved("10.0.0.5"))
                .header(X_FORWARDED_FOR, String.join(",", Collections.nCopies(40, "10.0.0.8")))
                .build());

        assertThat(registry.get(ForwardedHeaderCanonicalizationWebFilter.METRIC_NAME)
                .tag("outcome", "malformed").counter().count()).isEqualTo(1.0d);
        assertThat(registry.get(ForwardedHeaderCanonicalizationWebFilter.METRIC_NAME)
                .tag("outcome", "truncated").counter().count()).isEqualTo(1.0d);
        registry.getMeters().forEach(meter -> assertThat(meter.getId().getTags())
                .extracting(tag -> tag.getKey())
                .containsExactly("outcome"));
    }

    private static ForwardedHeaderCanonicalizationWebFilter filterWithTrustedCidrs(String... cidrs) {
        return new ForwardedHeaderCanonicalizationWebFilter(trusted(cidrs), null);
    }

    private static EdgeTrustedProxyProperties trusted(String... cidrs) {
        EdgeTrustedProxyProperties properties = new EdgeTrustedProxyProperties();
        properties.setEnabled(true);
        properties.setCidrs(List.of(cidrs));
        return properties;
    }

    private static MockServerHttpRequest.BaseBuilder<?> request(String path, InetSocketAddress remoteAddress) {
        return MockServerHttpRequest.get(path).remoteAddress(remoteAddress);
    }

    private static InetSocketAddress resolved(String address) {
        try {
            return new InetSocketAddress(InetAddress.getByName(address), 8080);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private static InetSocketAddress unresolved(String host) {
        return InetSocketAddress.createUnresolved(host, 8080);
    }

    private static CapturedRequest filter(
            ForwardedHeaderCanonicalizationWebFilter filter,
            MockServerHttpRequest request
    ) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        WebFilterChain chain = filteredExchange -> {
            downstream.set(filteredExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        return new CapturedRequest(exchange, downstream.get());
    }

    private static void assertCanonical(CapturedRequest capture, String expected) {
        assertThat(capture.downstream()).isNotNull();
        assertThat((String) capture.exchange().getAttribute(
                ForwardedHeaderCanonicalizationWebFilter.CANONICAL_CLIENT_IP_ATTRIBUTE))
                .isEqualTo(expected);
        assertThat(capture.downstream().getRequest().getHeaders().get(X_FORWARDED_FOR))
                .containsExactly(expected);
    }

    private static void assertNoCanonicalClient(CapturedRequest capture) {
        assertThat(capture.downstream()).isNotNull();
        assertThat((String) capture.exchange().getAttribute(
                ForwardedHeaderCanonicalizationWebFilter.CANONICAL_CLIENT_IP_ATTRIBUTE)).isNull();
        assertForwardingHeadersRemoved(capture.downstream());
        assertThat(capture.downstream().getRequest().getHeaders()).doesNotContainKey(X_FORWARDED_FOR);
    }

    private static void assertForwardingHeadersRemoved(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        assertThat(headers).doesNotContainKey(FORWARDED);
        assertThat(headers).doesNotContainKey(X_FORWARDED_HOST);
        assertThat(headers).doesNotContainKey(X_FORWARDED_PORT);
        assertThat(headers).doesNotContainKey(X_FORWARDED_PREFIX);
        assertThat(headers).doesNotContainKey(X_FORWARDED_PROTO);
        assertThat(headers).doesNotContainKey(X_REAL_IP);
    }

    private record CapturedRequest(ServerWebExchange exchange, ServerWebExchange downstream) {
    }
}
