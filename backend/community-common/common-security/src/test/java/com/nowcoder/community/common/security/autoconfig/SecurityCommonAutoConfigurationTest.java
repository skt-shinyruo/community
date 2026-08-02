package com.nowcoder.community.common.security.autoconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityCommonAutoConfigurationTest {

    private static final String ACCESS_PUBLIC_KEY = publicKey();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityCommonAutoConfiguration.class);

    @Test
    void context_shouldFailWhenPublicKeyInvalid() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.access-public-key=not-a-key",
                        "security.jwt.issuer=community-auth"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("security.jwt.access-public-key");
                });
    }

    @Test
    void context_shouldFailWhenIssuerMissing() {
        contextRunner
                .withPropertyValues("security.jwt.access-public-key=" + ACCESS_PUBLIC_KEY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("security.jwt.issuer");
                });
    }

    @Test
    void context_shouldFailWhenAudienceBlank() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.access-public-key=" + ACCESS_PUBLIC_KEY,
                        "security.jwt.issuer=community-auth",
                        "security.jwt.access-token-audience= "
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("security.jwt.access-token-audience");
                });
    }

    @Test
    void context_shouldStartWithPublicKeyOnly() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.access-public-key=" + ACCESS_PUBLIC_KEY,
                        "security.jwt.issuer=community-auth",
                        "security.jwt.access-token-audience=community-api"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                });
    }

    private static String publicKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
