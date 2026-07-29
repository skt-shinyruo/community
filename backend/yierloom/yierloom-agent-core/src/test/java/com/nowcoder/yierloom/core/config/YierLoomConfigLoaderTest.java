package com.nowcoder.yierloom.core.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YierLoomConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesFileEnvironmentSystemAndAgentArgumentPrecedence() throws Exception {
        Files.writeString(tempDir.resolve("yierloom.properties"), """
                yierloom.enabled=false
                yierloom.events.queue-capacity=10
                yierloom.plugins.http.slow-threshold=5s
                """);

        YierLoomConfig config = YierLoomConfigLoader.load(
                "config=yierloom.properties,plugins-dir=plugins,yierloom.events.queue-capacity=40",
                Map.of("yierloom.events.queue-capacity", "30"),
                Map.of(
                        "YIERLOOM_EVENTS_QUEUE_CAPACITY", "20",
                        "YIERLOOM_PLUGIN__HTTP__SLOW_THRESHOLD", "2s"),
                tempDir);

        assertThat(config.enabled()).isFalse();
        assertThat(config.eventQueueCapacity()).isEqualTo(40);
        assertThat(config.pluginDirectory()).contains(tempDir.resolve("plugins").normalize());
        assertThat(config.pluginConfig("http").getDuration("slow-threshold", Duration.ZERO))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(config.originForPluginKey("http", "slow-threshold").source())
                .isEqualTo(ConfigSource.ENVIRONMENT);
    }

    @Test
    void rejectsExplicitMalformedGlobalValues() {
        assertThatThrownBy(() -> YierLoomConfigLoader.load(
                "yierloom.enabled=yes", Map.of(), Map.of(), tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yierloom.enabled")
                .hasMessageNotContaining("yes");
    }

    @Test
    void neverRecognizesLegacyAgentNamespaces() {
        YierLoomConfig config = YierLoomConfigLoader.load(
                "runtime.diagnostics.enabled=true",
                Map.of("runtime.diagnostics.enabled", "true"),
                Map.of("RUNTIME_DIAGNOSTICS_ENABLED", "true"),
                tempDir);

        assertThat(config.enabled()).isFalse();
    }

    @Test
    void resolvesServiceNameThroughYierLoomOtelAndGenericFallbacks() {
        assertThat(loadWith(
                Map.of("otel.service.name", "otel-property"),
                Map.of("OTEL_SERVICE_NAME", "otel-env", "SERVICE_NAME", "generic")).serviceName())
                .isEqualTo("otel-property");
        assertThat(loadWith(
                Map.of("yierloom.service.name", "agent-name"),
                Map.of("OTEL_SERVICE_NAME", "otel-env")).serviceName())
                .isEqualTo("agent-name");
        assertThat(loadWith(Map.of(), Map.of("SERVICE_NAME", "generic")).serviceName())
                .isEqualTo("generic");
        assertThat(loadWith(Map.of(), Map.of()).serviceName()).isEqualTo("unknown");
    }

    @Test
    void warnsForUnknownGlobalKeyButLeavesPluginPrivateKeysToThePlugin() {
        YierLoomConfig config = YierLoomConfigLoader.load(
                "yierloom.unknown=true,yierloom.plugins.sample.private-key=value",
                Map.of(), Map.of(), tempDir);

        assertThat(config.warnings()).singleElement().asString().contains("yierloom.unknown");
        assertThat(config.pluginConfig("sample").findString("private-key")).contains("value");
    }

    @Test
    void rejectsMissingExplicitConfigurationFile() {
        assertThatThrownBy(() -> YierLoomConfigLoader.load(
                "config=missing.properties", Map.of(), Map.of(), tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yierloom.config");
    }

    @Test
    void attributesMalformedPluginDirectoryToItsKeyWithoutExposingTheValue() {
        String privateValue = "secret" + '\0' + "path";

        assertThatThrownBy(() -> YierLoomConfigLoader.load(
                "plugins-dir=" + privateValue, Map.of(), Map.of(), tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yierloom.plugins.dir")
                .hasMessageNotContaining("secret");
    }

    private YierLoomConfig loadWith(Map<String, String> system, Map<String, String> environment) {
        return YierLoomConfigLoader.load("", system, environment, tempDir);
    }
}
