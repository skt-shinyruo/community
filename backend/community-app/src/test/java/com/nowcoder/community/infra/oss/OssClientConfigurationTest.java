package com.nowcoder.community.infra.oss;

import com.nowcoder.community.auth.config.JwtCryptoConfig;
import com.nowcoder.community.common.observability.oss.OssRuntimeLogger;
import com.nowcoder.community.common.security.autoconfig.SecurityCommonAutoConfiguration;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.infra.observability.ObservedCommunityOssClient;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.OssServiceTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OssClientConfigurationTest {

    private static final Instant INITIAL_NOW = Instant.parse("2026-07-20T08:15:30Z");
    private static final String VALID_SECRET = "community-app-test-jwt-secret-32-bytes-minimum";
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
            JwtEncoderParameters parameters = context.getBean(RecordingJwtEncoder.class).latest();
            Map<String, Object> claims = parameters.getClaims().getClaims();

            assertThat(token).isEqualTo("encoded-service-token-1").doesNotStartWith("Bearer ");
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
            assertThat(parameters.getJwsHeader().getAlgorithm()).isEqualTo(MacAlgorithm.HS256);
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
            RecordingJwtEncoder encoder = context.getBean(RecordingJwtEncoder.class);
            MutableClock clock = context.getBean(MutableClock.class);

            assertThat(provider.tokenValue()).isEqualTo("encoded-service-token-1");
            Instant firstIssuedAt = (Instant) encoder.latest().getClaims().getClaims().get("iat");
            clock.advance(Duration.ofMinutes(1));
            assertThat(provider.tokenValue()).isEqualTo("encoded-service-token-2");
            Instant secondIssuedAt = (Instant) encoder.latest().getClaims().getClaims().get("iat");

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

    @Test
    void clientShouldRetainOptionalRuntimeObservation() {
        tokenContextRunner()
                .withBean(OssRuntimeLogger.class, () -> mock(OssRuntimeLogger.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CommunityOssClient.class))
                            .isInstanceOf(ObservedCommunityOssClient.class);
                });
    }

    private ApplicationContextRunner tokenContextRunner(String... settings) {
        return new ApplicationContextRunner()
                .withUserConfiguration(OssClientConfiguration.class)
                .withBean(JwtProperties.class, OssClientConfigurationTest::validJwtProperties)
                .withBean(RecordingJwtEncoder.class, RecordingJwtEncoder::new)
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
                invalidJwtSetting("security.jwt.hmac-secret", null),
                invalidJwtSetting("security.jwt.hmac-secret", ""),
                invalidJwtSetting("security.jwt.hmac-secret", "short-secret")
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
        settings.put("security.jwt.hmac-secret", VALID_SECRET);
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
        properties.setHmacSecret(VALID_SECRET);
        return properties;
    }

    private record InvalidSettings(String property, String[] properties) {

        @Override
        public String toString() {
            return property;
        }
    }

    static final class RecordingJwtEncoder implements JwtEncoder {

        private final List<JwtEncoderParameters> parameters = new ArrayList<>();

        @Override
        public Jwt encode(JwtEncoderParameters parameters) {
            this.parameters.add(parameters);
            Map<String, Object> claims = parameters.getClaims().getClaims();
            return new Jwt(
                    "encoded-service-token-" + this.parameters.size(),
                    (Instant) claims.get("iat"),
                    (Instant) claims.get("exp"),
                    parameters.getJwsHeader().getHeaders(),
                    claims
            );
        }

        JwtEncoderParameters latest() {
            return parameters.get(parameters.size() - 1);
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
