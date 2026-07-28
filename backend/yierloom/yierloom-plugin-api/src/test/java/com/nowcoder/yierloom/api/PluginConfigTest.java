package com.nowcoder.yierloom.api;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginConfigTest {

    @Test
    void readsTypedValuesWithoutMutatingInput() {
        Map<String, String> source = new LinkedHashMap<>(Map.of(
                "enabled", "true", "limit", "7", "interval", "250ms", "names", "a,b"));
        PluginConfig config = PluginConfig.of(source);
        source.put("limit", "99");

        assertThat(config.getBoolean("enabled", false)).isTrue();
        assertThat(config.getInt("limit", 1)).isEqualTo(7);
        assertThat(config.getDuration("interval", Duration.ofSeconds(1))).isEqualTo(Duration.ofMillis(250));
        assertThat(config.getStringList("names", List.of())).containsExactly("a", "b");
        assertThat(config.findInt("limit")).hasValue(7);
        assertThat(config.findLong("missing")).isEmpty();
        assertThat(config.requireDuration("interval")).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void rejectsAnExplicitMalformedValueInsteadOfUsingTheDefault() {
        PluginConfig config = PluginConfig.of(Map.of("sample-rate", "not-a-number"));

        assertThatThrownBy(() -> config.getDouble("sample-rate", 1.0))
                .isInstanceOf(PluginConfigurationException.class)
                .hasMessageContaining("sample-rate")
                .doesNotHaveToString("not-a-number");
    }

    @Test
    void rejectsMissingRequiredValueWithoutPrintingConfigurationData() {
        assertThatThrownBy(() -> PluginConfig.empty().requireString("token"))
                .isInstanceOf(PluginConfigurationException.class)
                .hasMessageContaining("token");
    }

    @Test
    void parsesSignedIsoDurations() {
        PluginConfig config = PluginConfig.of(Map.of(
                "negative", "-PT1S",
                "positive", "+PT1S"));

        assertThat(config.requireDuration("negative")).isEqualTo(Duration.ofSeconds(-1));
        assertThat(config.requireDuration("positive")).isEqualTo(Duration.ofSeconds(1));
    }
}
