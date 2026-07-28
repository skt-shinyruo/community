package com.nowcoder.yierloom.api;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YierLoomBridgeTest {

    @AfterEach
    void clearBridge() {
        YierLoomBridge.clearForTests();
    }

    @Test
    void installsOnceAndClearsOnlyByEndpointIdentity() {
        RecordingEndpoint first = new RecordingEndpoint();
        RecordingEndpoint second = new RecordingEndpoint();

        assertThat(YierLoomBridge.install(first)).isTrue();
        assertThat(YierLoomBridge.install(second)).isFalse();
        assertThat(YierLoomBridge.clear(second)).isFalse();
        assertThat(YierLoomBridge.emit("sample", DiagnosticEvent.builder("ready").build())).isTrue();
        assertThat(first.events).hasSize(1);
        assertThat(YierLoomBridge.clear(first)).isTrue();
    }

    private static final class RecordingEndpoint implements YierLoomBridge.Endpoint {
        private final List<DiagnosticEvent> events = new ArrayList<>();

        @Override
        public boolean observe(String pluginId, PluginObservation observation) {
            return true;
        }

        @Override
        public boolean emit(String pluginId, DiagnosticEvent event) {
            events.add(event);
            return true;
        }
    }
}
