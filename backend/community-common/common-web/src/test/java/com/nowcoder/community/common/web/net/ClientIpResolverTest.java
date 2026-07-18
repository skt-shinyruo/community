package com.nowcoder.community.common.web.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ClientIpResolverTest {

    @Test
    void shouldReturnRemoteSourceForNullRequest() {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8"));

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(null);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp(null, "remote"));
    }

    @Test
    void shouldIgnoreForwardedHeadersWhenPropertiesAreNull() {
        ClientIpResolver resolver = new ClientIpResolver(null);
        MockHttpServletRequest request = request("192.0.2.10", "198.51.100.20");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("192.0.2.10", "remote"));
    }

    @Test
    void shouldIgnoreForwardedHeadersWhenTrustedProxySupportIsDisabled() {
        TrustedProxyProperties properties = properties(false, List.of("0.0.0.0/0"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = request("192.0.2.10", "198.51.100.20");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("192.0.2.10", "remote"));
    }

    @Test
    void shouldIgnoreForwardedHeadersWhenEnabledWithoutTrustedCidrs() {
        ClientIpResolver resolver = new ClientIpResolver(properties(true, List.of()));
        MockHttpServletRequest request = request("192.0.2.10", "198.51.100.20");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("192.0.2.10", "remote"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("directOnlyResolverCases")
    void shouldNotReadForwardedHeadersWhenForwardedSupportIsInactive(
            String description,
            ClientIpResolver resolver
    ) {
        MockHttpServletRequest request = requestRejectingHeaderAccess("2001:0DB8:0:0:0:0:0:20");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("2001:db8::20", "remote"));
    }

    static Stream<Arguments> directOnlyResolverCases() {
        return Stream.of(
                Arguments.of("null properties", new ClientIpResolver(null)),
                Arguments.of(
                        "disabled properties",
                        new ClientIpResolver(properties(false, List.of("0.0.0.0/0")))
                ),
                Arguments.of(
                        "enabled without CIDRs",
                        new ClientIpResolver(properties(true, List.of()))
                )
        );
    }

    @Test
    void shouldIgnoreForwardedHeadersFromUntrustedDirectPeer() {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8"));
        MockHttpServletRequest request = request("192.0.2.10", "198.51.100.20");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("192.0.2.10", "remote"));
    }

    @Test
    void shouldResolveSingleForwardedHopFromTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8"));
        MockHttpServletRequest request = request("10.0.0.3", "198.51.100.20");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("198.51.100.20", "xff"));
    }

    @Test
    void shouldStripTrustedProxiesFromRightOfMultiHopChain() {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8"));
        MockHttpServletRequest request = request("10.0.0.3", "198.51.100.20, 10.0.0.1, 10.0.0.2");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("198.51.100.20", "xff"));
    }

    @Test
    void shouldSelectNearestUntrustedHopInsteadOfSpoofedLeftPrefix() {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8"));
        MockHttpServletRequest request = request(
                "10.0.0.3",
                "203.0.113.99, 198.51.100.20, 10.0.0.1, 10.0.0.2"
        );

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("198.51.100.20", "xff"));
    }

    @ParameterizedTest
    @MethodSource("invalidForwardedChains")
    void shouldFallBackToDirectPeerWhenAnyForwardedItemIsInvalid(String forwardedFor) {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8"));
        MockHttpServletRequest request = request("10.0.0.3", forwardedFor);

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("10.0.0.3", "remote"));
    }

    static Stream<String> invalidForwardedChains() {
        return Stream.of(
                "198.51.100.20, not-an-ip, 10.0.0.2",
                "198.51.100.20,,10.0.0.2"
        );
    }

    @Test
    void shouldFlattenAllForwardedHeaderLinesInOriginalOrder() {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8"));
        MockHttpServletRequest request = request(
                "10.0.0.3",
                "203.0.113.99, 10.0.0.1",
                "198.51.100.20, 10.0.0.2"
        );

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("198.51.100.20", "xff"));
    }

    @Test
    void shouldStopEnumeratingHeaderLinesAfterThirtyThirdHop() {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8"));
        String overLimitLine = String.join(",", Collections.nCopies(33, "198.51.100.20"));
        MockHttpServletRequest request = requestRejectingAccessAfterFirstHeader("10.0.0.3", overLimitLine);

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("10.0.0.3", "remote"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("literalNormalizationCases")
    void shouldNormalizeLiteralAddressesWithoutReturningHostnames(
            String description,
            String directPeer,
            String forwardedFor,
            String expectedClientIp
    ) {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8", "2001:db8:ffff::/48"));
        MockHttpServletRequest request = request(directPeer, forwardedFor);

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp(expectedClientIp, "xff"));
    }

    static Stream<Arguments> literalNormalizationCases() {
        return Stream.of(
                Arguments.of("IPv4", "10.0.0.3", "198.51.100.20", "198.51.100.20"),
                Arguments.of("IPv6", "2001:DB8:FFFF:0:0:0:0:3", "2001:0DB9:0:0:0:0:0:20", "2001:db9::20"),
                Arguments.of("IPv4-mapped IPv6", "::FFFF:10.0.0.3", "::ffff:192.0.2.20", "192.0.2.20")
        );
    }

    @Test
    void shouldNormalizeDirectPeerWhenForwardedHeadersAreDisabled() {
        ClientIpResolver resolver = new ClientIpResolver(null);
        MockHttpServletRequest request = request("2001:0DB8:0:0:0:0:0:20", "198.51.100.20");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("2001:db8::20", "remote"));
    }

    @Test
    void shouldRejectInvalidEnabledCidrAtConstruction() {
        TrustedProxyProperties properties = properties(true, List.of("proxy.internal/24"));

        assertThatThrownBy(() -> new ClientIpResolver(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted CIDR");
    }

    @Test
    void shouldRejectHostnameWithoutDnsLookup() {
        String hostname = UUID.randomUUID() + ".invalid";
        ClientIpResolver resolver = new ClientIpResolver(null);
        MockHttpServletRequest request = request(hostname, "198.51.100.20");

        ClientIpResolver.ResolvedClientIp result = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> resolver.resolve(request)
        );

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp(null, "remote"));
    }

    @Test
    void shouldRejectLocalhostAsDirectPeerWithoutDnsLookup() {
        ClientIpResolver resolver = new ClientIpResolver(null);
        MockHttpServletRequest request = request("localhost", "198.51.100.20");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp(null, "remote"));
    }

    @Test
    void shouldFallBackToDirectPeerWhenForwardedItemIsLocalhost() {
        ClientIpResolver resolver = new ClientIpResolver(trusted("10.0.0.0/8"));
        MockHttpServletRequest request = request("10.0.0.3", "198.51.100.20, localhost, 10.0.0.2");

        ClientIpResolver.ResolvedClientIp result = resolver.resolve(request);

        assertThat(result).isEqualTo(new ClientIpResolver.ResolvedClientIp("10.0.0.3", "remote"));
    }

    private static TrustedProxyProperties trusted(String... cidrs) {
        return properties(true, List.of(cidrs));
    }

    private static TrustedProxyProperties properties(boolean enabled, List<String> cidrs) {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.setEnabled(enabled);
        properties.setCidrs(cidrs);
        return properties;
    }

    private static MockHttpServletRequest request(String remoteAddr, String... forwardedHeaderLines) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        for (String headerLine : forwardedHeaderLines) {
            request.addHeader("X-Forwarded-For", headerLine);
        }
        return request;
    }

    private static MockHttpServletRequest requestRejectingHeaderAccess(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public Enumeration<String> getHeaders(String name) {
                throw new AssertionError("Forwarded headers must not be read");
            }
        };
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static MockHttpServletRequest requestRejectingAccessAfterFirstHeader(
            String remoteAddr,
            String firstHeaderLine
    ) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public Enumeration<String> getHeaders(String name) {
                return new Enumeration<>() {
                    private boolean firstLineRead;

                    @Override
                    public boolean hasMoreElements() {
                        if (firstLineRead) {
                            throw new AssertionError("Headers after the over-limit line must not be enumerated");
                        }
                        return true;
                    }

                    @Override
                    public String nextElement() {
                        if (firstLineRead) {
                            throw new AssertionError("Headers after the over-limit line must not be read");
                        }
                        firstLineRead = true;
                        return firstHeaderLine;
                    }
                };
            }
        };
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
