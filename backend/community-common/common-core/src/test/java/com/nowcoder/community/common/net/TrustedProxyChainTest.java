package com.nowcoder.community.common.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedProxyChainTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("literalAndCidrCases")
    void shouldResolveLiteralAddressesAgainstTrustedCidrs(
            String description,
            List<String> trustedCidrs,
            String directPeer,
            List<String> forwardedFor,
            String expectedClientIp,
            TrustedProxyChain.Source expectedSource
    ) {
        TrustedProxyChain resolver = new TrustedProxyChain(trustedCidrs);

        TrustedProxyChain.Resolution resolution = resolver.resolve(directPeer, forwardedFor);

        assertThat(resolution).isEqualTo(new TrustedProxyChain.Resolution(expectedClientIp, expectedSource));
    }

    static Stream<Arguments> literalAndCidrCases() {
        return Stream.of(
                Arguments.of(
                        "IPv4",
                        List.of("10.0.0.0/8"),
                        "10.20.30.40",
                        List.of("198.51.100.1"),
                        "198.51.100.1",
                        TrustedProxyChain.Source.FORWARDED_CHAIN
                ),
                Arguments.of(
                        "compressed IPv6",
                        List.of("2001:db8::/32"),
                        "2001:0DB8:0:0::5",
                        List.of("2001:0DB9:0000::0001"),
                        "2001:db9::1",
                        TrustedProxyChain.Source.FORWARDED_CHAIN
                ),
                Arguments.of(
                        "IPv4-mapped IPv6",
                        List.of("::ffff:10.0.0.0/104"),
                        "::FFFF:10.20.30.40",
                        List.of("::ffff:192.0.2.1"),
                        "192.0.2.1",
                        TrustedProxyChain.Source.FORWARDED_CHAIN
                ),
                Arguments.of(
                        "IPv6 /0",
                        List.of("::/0"),
                        "2001:db8::5",
                        List.of("198.51.100.1"),
                        "198.51.100.1",
                        TrustedProxyChain.Source.FORWARDED_CHAIN
                ),
                Arguments.of(
                        "IPv4 /32",
                        List.of("203.0.113.9/32"),
                        "203.0.113.9",
                        List.of("198.51.100.1"),
                        "198.51.100.1",
                        TrustedProxyChain.Source.FORWARDED_CHAIN
                ),
                Arguments.of(
                        "IPv6 /128",
                        List.of("2001:db8::5/128"),
                        "2001:0db8:0:0:0:0:0:5",
                        List.of("2001:db9::1"),
                        "2001:db9::1",
                        TrustedProxyChain.Source.FORWARDED_CHAIN
                ),
                Arguments.of(
                        "IPv4 CIDR does not trust an IPv6 peer",
                        List.of("0.0.0.0/0"),
                        "2001:0DB8:0:0:0:0:0:5",
                        List.of("198.51.100.1"),
                        "2001:db8::5",
                        TrustedProxyChain.Source.DIRECT_PEER
                ),
                Arguments.of(
                        "IPv6 CIDR does not trust an IPv4 peer",
                        List.of("::/0"),
                        "203.0.113.9",
                        List.of("198.51.100.1"),
                        "203.0.113.9",
                        TrustedProxyChain.Source.DIRECT_PEER
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("proxyChainCases")
    void shouldStripOnlyTrustedHopsFromRightToLeft(
            String description,
            String directPeer,
            List<String> forwardedFor,
            String expectedClientIp,
            TrustedProxyChain.Source expectedSource
    ) {
        TrustedProxyChain resolver = new TrustedProxyChain(List.of("10.0.0.0/8"));

        TrustedProxyChain.Resolution resolution = resolver.resolve(directPeer, forwardedFor);

        assertThat(resolution).isEqualTo(new TrustedProxyChain.Resolution(expectedClientIp, expectedSource));
    }

    static Stream<Arguments> proxyChainCases() {
        return Stream.of(
                Arguments.of(
                        "untrusted direct peer ignores X-Forwarded-For",
                        "203.0.113.9",
                        List.of("198.51.100.1"),
                        "203.0.113.9",
                        TrustedProxyChain.Source.DIRECT_PEER
                ),
                Arguments.of(
                        "trusted direct peer accepts one untrusted hop",
                        "10.0.0.5",
                        List.of("198.51.100.1"),
                        "198.51.100.1",
                        TrustedProxyChain.Source.FORWARDED_CHAIN
                ),
                Arguments.of(
                        "trusted direct peer strips another trusted proxy",
                        "10.0.0.5",
                        List.of("198.51.100.1", "10.0.0.4"),
                        "198.51.100.1",
                        TrustedProxyChain.Source.FORWARDED_CHAIN
                ),
                Arguments.of(
                        "spoofed left prefix cannot override the nearest untrusted hop",
                        "10.0.0.5",
                        List.of("192.0.2.66", "198.51.100.1", "10.0.0.4"),
                        "198.51.100.1",
                        TrustedProxyChain.Source.FORWARDED_CHAIN
                )
        );
    }

    @ParameterizedTest
    @MethodSource("malformedHeaderHops")
    void shouldFallBackToDirectPeerForMalformedHeaderHop(String malformedHop) {
        TrustedProxyChain resolver = new TrustedProxyChain(List.of("10.0.0.0/8"));

        TrustedProxyChain.Resolution resolution = resolver.resolve(
                "10.0.0.5",
                Collections.singletonList(malformedHop)
        );

        assertThat(resolution).isEqualTo(
                new TrustedProxyChain.Resolution("10.0.0.5", TrustedProxyChain.Source.DIRECT_PEER)
        );
    }

    static Stream<String> malformedHeaderHops() {
        return Stream.of(
                null,
                "",
                "example.com",
                "1.2.3.4:80",
                "[::1]:80",
                "fe80::1%eth0",
                "\n198.51.100.1",
                "198.51.100.1\r",
                "\t198.51.100.1",
                "198.51.100.1\u007f",
                "2001:db8:::1",
                "2001:db8::1::2",
                "999.1.1.1"
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDirectPeers")
    void shouldReturnNoUsableAddressForInvalidDirectPeer(String directPeer) {
        TrustedProxyChain resolver = new TrustedProxyChain(List.of("10.0.0.0/8"));

        TrustedProxyChain.Resolution resolution = resolver.resolve(directPeer, List.of("198.51.100.1"));

        assertThat(resolution).isEqualTo(
                new TrustedProxyChain.Resolution(null, TrustedProxyChain.Source.DIRECT_PEER)
        );
    }

    static Stream<String> invalidDirectPeers() {
        return Stream.of(
                null,
                "",
                "example.com",
                "1.2.3.4:80",
                "[::1]:80",
                "fe80::1%eth0",
                "\n198.51.100.1",
                "198.51.100.1\r"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "10.0.0.1",
            "example.com/24",
            "1.2.3.4:80/24",
            "[::1]/128",
            "fe80::1%eth0/64",
            "10.0.0.0/-1",
            "10.0.0.0/+8",
            "10.0.0.0/33",
            "2001:db8::/129",
            "2001:db8::/64/1"
    })
    void shouldRejectInvalidConfiguredCidr(String invalidCidr) {
        assertThatThrownBy(() -> new TrustedProxyChain(List.of(invalidCidr)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullConfiguredCidr() {
        assertThatThrownBy(() -> new TrustedProxyChain(Collections.singletonList(null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrustedProxyChain(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptOrdinarySpacesAroundHeaderHops() {
        TrustedProxyChain resolver = new TrustedProxyChain(List.of("10.0.0.0/8"));

        TrustedProxyChain.Resolution resolution = resolver.resolve(
                "10.0.0.5",
                List.of(" 198.51.100.1 ", " 10.0.0.4 ")
        );

        assertThat(resolution).isEqualTo(
                new TrustedProxyChain.Resolution("198.51.100.1", TrustedProxyChain.Source.FORWARDED_CHAIN)
        );
    }

    @Test
    void shouldAllowAtMostThirtyTwoForwardedHops() {
        TrustedProxyChain resolver = new TrustedProxyChain(List.of("10.0.0.0/8"));
        List<String> forwardedFor = new ArrayList<>();
        forwardedFor.add("198.51.100.1");
        for (int index = 1; index < 32; index++) {
            forwardedFor.add("10.0.0.4");
        }

        TrustedProxyChain.Resolution resolution = resolver.resolve("10.0.0.5", forwardedFor);

        assertThat(resolution).isEqualTo(
                new TrustedProxyChain.Resolution("198.51.100.1", TrustedProxyChain.Source.FORWARDED_CHAIN)
        );
    }

    @Test
    void shouldFallBackToDirectPeerWhenForwardedChainExceedsThirtyTwoHops() {
        TrustedProxyChain resolver = new TrustedProxyChain(List.of("10.0.0.0/8"));
        List<String> forwardedFor = new ArrayList<>();
        forwardedFor.add("198.51.100.1");
        for (int index = 1; index < 33; index++) {
            forwardedFor.add("10.0.0.4");
        }

        TrustedProxyChain.Resolution resolution = resolver.resolve("10.0.0.5", forwardedFor);

        assertThat(resolution).isEqualTo(
                new TrustedProxyChain.Resolution("10.0.0.5", TrustedProxyChain.Source.DIRECT_PEER)
        );
    }

    @Test
    void shouldFallBackToDirectPeerWhenHeaderHopExceedsSixtyFourCharacters() {
        TrustedProxyChain resolver = new TrustedProxyChain(List.of("10.0.0.0/8"));

        TrustedProxyChain.Resolution resolution = resolver.resolve("10.0.0.5", List.of(" ".repeat(65)));

        assertThat(resolution).isEqualTo(
                new TrustedProxyChain.Resolution("10.0.0.5", TrustedProxyChain.Source.DIRECT_PEER)
        );
    }

    @Test
    void shouldFallBackToDirectPeerWhenForwardedChainIsNullOrContainsOnlyTrustedHops() {
        TrustedProxyChain resolver = new TrustedProxyChain(List.of("10.0.0.0/8"));

        assertThat(resolver.resolve("10.0.0.5", null)).isEqualTo(
                new TrustedProxyChain.Resolution("10.0.0.5", TrustedProxyChain.Source.DIRECT_PEER)
        );
        assertThat(resolver.resolve("10.0.0.5", List.of("10.0.0.4"))).isEqualTo(
                new TrustedProxyChain.Resolution("10.0.0.5", TrustedProxyChain.Source.DIRECT_PEER)
        );
    }
}
