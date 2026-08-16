package com.nowcoder.community.infra.oss;

import com.nowcoder.community.auth.config.JwtCryptoConfig;
import com.nowcoder.community.common.security.autoconfig.SecurityCommonAutoConfiguration;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.oss.client.OssServiceTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OssClientConfigurationTest {

    private static final Instant INITIAL_NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final String VALID_SERVICE_SECRET = "community-app-test-service-jwt-secret-32-bytes-minimum";
    private static final KeyPair ACCESS_KEY_PAIR = rsaKeyPair();
    private static final Map<String, String> VALID_OSS_SETTINGS = Map.of(
            "oss.client.base-url", "https://oss.example.test",
            "oss.client.service-subject", "community-app",
            "oss.client.audience", "community-oss",
            "oss.client.scope", "oss.internal",
            "oss.client.token-ttl", "PT4M"
    );

    @Test
    void serviceTokenShouldUseExactScopedClaimsAndReturnRawJwt() {
        tokenContextRunner().run(context -> {
            assertThat(context).hasNotFailed();

            String token = context.getBean(OssServiceTokenProvider.class).tokenValue();
            var decoded = JwtCodecs.serviceTokenDecoder(
                    validJwtProperties(), "community-auth", "community-oss"
            ).decode(token);
            Map<String, Object> claims = decoded.getClaims();

            assertThat(token).doesNotStartWith("Bearer ");
            assertThat(context.getBean(OssClientProperties.class))
                    .extracting(
                            OssClientProperties::baseUrl,
                            OssClientProperties::serviceSubject,
                            OssClientProperties::audience,
                            OssClientProperties::scope,
                            OssClientProperties::tokenTtl
                    )
                    .containsExactly(
                            "https://oss.example.test",
                            "community-app",
                            "community-oss",
                            "oss.internal",
                            Duration.ofMinutes(4)
                    );
            assertThat(decoded.getHeaders())
                    .containsEntry("alg", MacAlgorithm.HS256.getName())
                    .containsEntry("typ", JwtCodecs.SERVICE_TOKEN_TYPE);
            assertThat(claims)
                    .containsEntry("iss", "community-auth")
                    .containsEntry("sub", "community-app")
                    .containsEntry("aud", List.of("community-oss"))
                    .containsEntry("scope", "oss.internal")
                    .containsEntry("iat", INITIAL_NOW)
                    .containsEntry("exp", INITIAL_NOW.plus(Duration.ofMinutes(4)));
            Duration lifetime = Duration.between((Instant) claims.get("iat"), (Instant) claims.get("exp"));
            assertThat(lifetime).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5));
        });
    }

    @Test
    void serviceTokenShouldReadTheInjectedClockForEveryToken() {
        tokenContextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            OssServiceTokenProvider provider = context.getBean(OssServiceTokenProvider.class);
            MutableClock clock = context.getBean(MutableClock.class);

            String firstToken = provider.tokenValue();
            Instant firstIssuedAt = JwtCodecs.serviceTokenDecoder(
                    validJwtProperties(), "community-auth", "community-oss"
            ).decode(firstToken).getIssuedAt();
            clock.advance(Duration.ofMinutes(1));
            String secondToken = provider.tokenValue();
            Instant secondIssuedAt = JwtCodecs.serviceTokenDecoder(
                    validJwtProperties(), "community-auth", "community-oss"
            ).decode(secondToken).getIssuedAt();

            assertThat(secondToken).isNotEqualTo(firstToken);
            assertThat(firstIssuedAt).isEqualTo(INITIAL_NOW);
            assertThat(secondIssuedAt).isEqualTo(INITIAL_NOW.plus(Duration.ofMinutes(1)));
        });
    }

    @ParameterizedTest(name = "rejects invalid OSS client setting: {0}")
    @MethodSource("invalidOssClientSettings")
    void invalidOssClientSettingsShouldFailBeforeClientCreation(InvalidSettings invalid) {
        tokenContextRunner(invalid.properties()).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(invalid.property());
        });
    }

    @ParameterizedTest(name = "rejects invalid shared JWT setting: {0}")
    @MethodSource("invalidSharedJwtSettings")
    void invalidSharedJwtSettingsShouldFailClosed(InvalidSettings invalid) {
        sharedJwtContextRunner(invalid.properties()).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(invalid.property());
        });
    }

    private ApplicationContextRunner tokenContextRunner(String... settings) {
        return new ApplicationContextRunner()
                .withUserConfiguration(OssClientConfiguration.class)
                .withBean(JwtProperties.class, OssClientConfigurationTest::validJwtProperties)
                .withBean(MutableClock.class, () -> new MutableClock(INITIAL_NOW))
                .withPropertyValues(settings);
    }

    private ApplicationContextRunner tokenContextRunner() {
        return tokenContextRunner(properties(VALID_OSS_SETTINGS));
    }

    private ApplicationContextRunner sharedJwtContextRunner(String... jwtSettings) {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        SecurityCommonAutoConfiguration.class,
                        JwtCryptoConfig.class,
                        OssClientConfiguration.class
                )
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues(properties(VALID_OSS_SETTINGS))
                .withPropertyValues(jwtSettings);
    }

    private static Stream<InvalidSettings> invalidOssClientSettings() {
        return Stream.of(
                invalidOssSetting("oss.client.base-url", null),
                invalidOssSetting("oss.client.base-url", ""),
                invalidOssSetting("oss.client.base-url", "not-a-url"),
                invalidOssSetting("oss.client.base-url", "ftp://oss.example.test"),
                invalidOssSetting("oss.client.service-subject", null),
                invalidOssSetting("oss.client.service-subject", ""),
                invalidOssSetting("oss.client.service-subject", "community app"),
                invalidOssSetting("oss.client.audience", null),
                invalidOssSetting("oss.client.audience", ""),
                invalidOssSetting("oss.client.audience", "community oss"),
                invalidOssSetting("oss.client.scope", null),
                invalidOssSetting("oss.client.scope", ""),
                invalidOssSetting("oss.client.scope", "oss internal"),
                invalidOssSetting("oss.client.token-ttl", null),
                invalidOssSetting("oss.client.token-ttl", ""),
                invalidOssSetting("oss.client.token-ttl", "PT0S"),
                invalidOssSetting("oss.client.token-ttl", "-PT1S"),
                invalidOssSetting("oss.client.token-ttl", "PT5M0.001S"),
                invalidOssSetting("oss.client.token-ttl", "not-a-duration")
        );
    }

    private static Stream<InvalidSettings> invalidSharedJwtSettings() {
        return Stream.of(
                invalidJwtSetting("security.jwt.issuer", null),
                invalidJwtSetting("security.jwt.issuer", ""),
                invalidJwtSetting("security.jwt.access-public-key", null),
                invalidJwtSetting("security.jwt.access-public-key", ""),
                invalidJwtSetting("security.jwt.access-private-key", null),
                invalidJwtSetting("security.jwt.access-private-key", ""),
                invalidJwtSetting("security.jwt.service-hmac-secret", null),
                invalidJwtSetting("security.jwt.service-hmac-secret", ""),
                invalidJwtSetting("security.jwt.service-hmac-secret", "short-secret")
        );
    }

    private static InvalidSettings invalidOssSetting(String property, String value) {
        Map<String, String> settings = new LinkedHashMap<>(VALID_OSS_SETTINGS);
        replaceOrRemove(settings, property, value);
        return new InvalidSettings(property, properties(settings));
    }

    private static InvalidSettings invalidJwtSetting(String property, String value) {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("security.jwt.issuer", "community-auth");
        settings.put("security.jwt.access-public-key", accessPublicKey());
        settings.put("security.jwt.access-private-key", accessPrivateKey());
        settings.put("security.jwt.access-token-audience", "community-api");
        settings.put("security.jwt.service-hmac-secret", VALID_SERVICE_SECRET);
        replaceOrRemove(settings, property, value);
        return new InvalidSettings(property, properties(settings));
    }

    private static void replaceOrRemove(Map<String, String> settings, String property, String value) {
        if (value == null) {
            settings.remove(property);
        } else {
            settings.put(property, value);
        }
    }

    private static String[] properties(Map<String, String> settings) {
        return settings.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private static JwtProperties validJwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer(" community-auth ");
        properties.setServiceHmacSecret(VALID_SERVICE_SECRET);
        return properties;
    }

    private static String accessPublicKey() {
        return Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPublic().getEncoded());
    }

    private static String accessPrivateKey() {
        return Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPrivate().getEncoded());
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record InvalidSettings(String property, String[] properties) {

        @Override
        public String toString() {
            return property;
        }
    }

    static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
