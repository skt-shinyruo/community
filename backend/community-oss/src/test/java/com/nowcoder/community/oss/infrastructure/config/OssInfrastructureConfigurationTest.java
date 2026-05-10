package com.nowcoder.community.oss.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
