package com.nowcoder.community.user.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R2ClientConfigUnitTest {

    @Test
    void shouldFailWhenEndpointMissing() {
        R2Properties props = new R2Properties();
        props.setEndpoint(" ");
        props.setAccessKey("ak");
        props.setSecretKey("sk");

        R2ClientConfig config = new R2ClientConfig();
        assertThatThrownBy(() -> config.r2S3Client(props))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("r2.endpoint");
    }

    @Test
    void shouldFailWhenCredentialsMissing() {
        R2Properties props = new R2Properties();
        props.setEndpoint("https://example.invalid");
        props.setAccessKey("");
        props.setSecretKey("sk");

        R2ClientConfig config = new R2ClientConfig();
        assertThatThrownBy(() -> config.r2S3Client(props))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("r2.access-key");
    }

    @Test
    void shouldBuildS3ClientWhenConfigured() {
        R2Properties props = new R2Properties();
        props.setEndpoint("https://example.invalid");
        props.setAccessKey("ak");
        props.setSecretKey("sk");
        props.setRegion("auto");
        props.setPathStyle(true);

        R2ClientConfig config = new R2ClientConfig();
        try (var client = config.r2S3Client(props)) {
            assertThat(client).isNotNull();
        }
    }
}

