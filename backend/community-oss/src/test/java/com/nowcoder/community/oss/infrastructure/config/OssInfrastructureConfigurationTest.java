package com.nowcoder.community.oss.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssInfrastructureConfigurationTest {

    @Test
    void objectStorePropertiesShouldDefaultToGarageSigningRegion() {
        OssProperties.ObjectStoreProperties properties = new OssProperties.ObjectStoreProperties();

        assertThat(properties.region()).isEqualTo("garage");
    }

    @Test
    void objectStoreRegionShouldBeConfigurable() {
        OssProperties.ObjectStoreProperties properties = new OssProperties.ObjectStoreProperties();

        properties.setRegion("us-east-1");

        assertThat(properties.region()).isEqualTo("us-east-1");
    }

    @Test
    void objectStoreApiCallTimeoutShouldDefaultToThirtyMinutes() {
        OssProperties.ObjectStoreProperties properties = new OssProperties.ObjectStoreProperties();

        assertThat(properties.apiCallTimeout()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void objectStoreApiCallTimeoutShouldKeepValuesBelowTheMaximum() {
        OssProperties.ObjectStoreProperties properties = new OssProperties.ObjectStoreProperties();

        properties.setApiCallTimeout(Duration.ofMinutes(20));

        assertThat(properties.apiCallTimeout()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void objectStoreApiCallTimeoutShouldBindFromConfiguration() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "oss.object-store.api-call-timeout", "20m"
        )));

        OssProperties properties = binder.bind("oss", Bindable.of(OssProperties.class)).get();

        assertThat(properties.objectStore().apiCallTimeout()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void objectStoreApiCallTimeoutShouldRejectValuesAboveThirtyMinutes() {
        OssProperties.ObjectStoreProperties properties = new OssProperties.ObjectStoreProperties();

        properties.setApiCallTimeout(Duration.ofHours(2));

        assertThatThrownBy(properties::apiCallTimeout)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("oss.object-store.api-call-timeout must not exceed 30 minutes");
    }

    @Test
    void objectStoreApiCallTimeoutShouldRejectNonPositiveValues() {
        OssProperties.ObjectStoreProperties properties = new OssProperties.ObjectStoreProperties();
        properties.setApiCallTimeout(Duration.ZERO);

        assertThatThrownBy(properties::apiCallTimeout)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("oss.object-store.api-call-timeout must be positive");
    }

    @Test
    void s3ClientShouldApplyTheConfiguredApiCallTimeout() {
        OssProperties.ObjectStoreProperties properties = new OssProperties.ObjectStoreProperties();
        properties.setApiCallTimeout(Duration.ofMinutes(20));

        try (S3Client client = new OssInfrastructureConfiguration().s3Client(properties)) {
            assertThat(client.serviceClientConfiguration()
                    .overrideConfiguration()
                    .apiCallTimeout())
                    .contains(Duration.ofMinutes(20));
        }
    }
}
