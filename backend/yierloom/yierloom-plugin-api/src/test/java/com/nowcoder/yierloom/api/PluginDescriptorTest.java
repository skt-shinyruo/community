package com.nowcoder.yierloom.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginDescriptorTest {

    @Test
    void acceptsStableDescriptor() {
        PluginDescriptor descriptor = new PluginDescriptor("http-client", "HTTP Client", "1.2.3", "1.0.0", false, 20);

        assertThat(descriptor.id()).isEqualTo("http-client");
        assertThat(descriptor.order()).isEqualTo(20);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "HTTP", "http.client", "1http", "http_client"})
    void rejectsInvalidIds(String id) {
        assertThatThrownBy(() -> new PluginDescriptor(id, "name", "1.0.0", "1.0.0", true, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
