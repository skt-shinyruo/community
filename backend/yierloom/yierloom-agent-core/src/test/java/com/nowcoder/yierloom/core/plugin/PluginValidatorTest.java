package com.nowcoder.yierloom.core.plugin;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.core.config.ConfigOrigin;
import com.nowcoder.yierloom.core.config.ConfigSource;
import com.nowcoder.yierloom.core.config.YierLoomConfig;
import com.nowcoder.yierloom.sdk.InstrumentationCapability;
import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginValidatorTest {
    private final PluginValidator validator = new PluginValidator();

    @Test
    void rejectsEveryExternalDuplicateAndNeverOverridesABuiltIn() {
        PluginValidator.ValidationResult result = validator.validate(List.of(
                builtIn(runtimePlugin("method", 10)),
                external("a.jar", runtimePlugin("method", 0)),
                external("b.jar", runtimePlugin("duplicate", 20)),
                external("c.jar", runtimePlugin("duplicate", 30))), config(Map.of(), Map.of()));

        assertThat(result.plugins()).extracting(plugin -> plugin.descriptor().id())
                .containsExactly("method");
        assertThat(result.reports()).filteredOn(report -> report.state() == PluginState.FAILED)
                .extracting(PluginReport::pluginId)
                .containsExactlyInAnyOrder("method", "duplicate", "duplicate");
        assertThat(result.reports()).filteredOn(report -> report.state() == PluginState.FAILED)
                .extracting(PluginReport::reasonCode)
                .containsExactlyInAnyOrder("RESERVED_ID", "DUPLICATE_ID", "DUPLICATE_ID");
    }

    @Test
    void deeplyWrappedFatalFromAProviderDeclarationIsRethrown() {
        YierLoomPlugin provider = new BasePlugin("fatal", 0, "1.0.0") {
            @Override
            public PluginDescriptor descriptor() {
                Throwable failure = new OutOfMemoryError("fatal");
                for (int depth = 0; depth < 100; depth++) {
                    failure = new IllegalStateException("wrapped", failure);
                }
                throw (RuntimeException) failure;
            }
        };

        assertThatThrownBy(() -> validator.validate(
                List.of(builtIn(provider)), config(Map.of(), Map.of())))
                .isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    void rejectsEveryBuiltInThatSharesAnId() {
        PluginValidator.ValidationResult result = validator.validate(List.of(
                builtIn(runtimePlugin("duplicate", 10)),
                builtIn(runtimePlugin("duplicate", 20))), config(Map.of(), Map.of()));

        assertThat(result.plugins()).isEmpty();
        assertThat(result.reports()).extracting(PluginReport::reasonCode)
                .containsExactly("DUPLICATE_ID", "DUPLICATE_ID");
    }

    @Test
    void validatesCapabilitiesAndInstrumentationDeclarations() {
        YierLoomPlugin rootOnly = plugin("root-only", 0);
        YierLoomPlugin emptyInstrumentation = instrumentationPlugin("empty", 1, List.of());
        YierLoomPlugin invalidModules = instrumentationPlugin(
                "invalid-modules", 2, List.of(module("same"), module("same")));
        YierLoomPlugin combinedWithoutModules = combinedPlugin("combined", 3, List.of());

        PluginValidator.ValidationResult result = validator.validate(List.of(
                builtIn(rootOnly),
                builtIn(emptyInstrumentation),
                builtIn(invalidModules),
                builtIn(combinedWithoutModules)), config(Map.of(), Map.of()));

        assertThat(result.plugins()).extracting(plugin -> plugin.descriptor().id())
                .containsExactly("combined");
        assertThat(result.reports()).filteredOn(report -> report.state() == PluginState.FAILED)
                .extracting(PluginReport::reasonCode)
                .containsOnly("INVALID_CAPABILITY");
    }

    @Test
    void rejectsAllPluginsParticipatingInAHelperCollision() {
        YierLoomPlugin first = instrumentationPlugin(
                "first", 30, List.of(module("first-module", "fixture.SharedHelper")));
        YierLoomPlugin second = instrumentationPlugin(
                "second", 10, List.of(module("second-module", "fixture.SharedHelper")));
        YierLoomPlugin independent = instrumentationPlugin(
                "independent", 20, List.of(module("independent-module", "fixture.OwnHelper")));

        PluginValidator.ValidationResult result = validator.validate(List.of(
                external("a.jar", first),
                external("b.jar", second),
                external("c.jar", independent)), config(Map.of(), Map.of()));

        assertThat(result.plugins()).extracting(plugin -> plugin.descriptor().id())
                .containsExactly("independent");
        assertThat(result.reports()).filteredOn(report ->
                        "HELPER_NAME_COLLISION".equals(report.reasonCode()))
                .extracting(PluginReport::pluginId)
                .containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void reportsConfigurationKeyAndOriginWithoutLeakingTheRawValue() {
        YierLoomPlugin provider = new InstrumentedPlugin("configured", 0) {
            @Override
            public List<InstrumentationModule> instrumentations(PluginConfig config) {
                config.requireDuration("interval");
                return List.of(module("configured-module"));
            }
        };
        ConfigOrigin origin = new ConfigOrigin(ConfigSource.SYSTEM_PROPERTY, "system property");
        YierLoomConfig config = config(
                Map.of("configured", PluginConfig.of(Map.of("interval", "private-raw-value"))),
                Map.of("yierloom.plugins.configured.interval", origin));

        PluginValidator.ValidationResult result = validator.validate(
                List.of(builtIn(provider)), config);

        assertThat(result.plugins()).isEmpty();
        assertThat(result.reports()).singleElement().satisfies(report -> {
            assertThat(report.reasonCode()).isEqualTo("INVALID_CONFIG");
            assertThat(report.summary()).contains("interval", "system property");
            assertThat(report.summary()).doesNotContain("private-raw-value");
        });
    }

    @Test
    void skipsInstrumentationForDisabledPluginsAndInvokesEnabledProvidersOnce() {
        AtomicInteger disabledCalls = new AtomicInteger();
        AtomicInteger enabledCalls = new AtomicInteger();
        YierLoomPlugin disabled = countingInstrumentation("disabled", 10, disabledCalls);
        YierLoomPlugin enabled = countingInstrumentation("enabled", 10, enabledCalls);
        YierLoomConfig config = config(Map.of(
                "disabled", PluginConfig.of(Map.of("enabled", "false"))), Map.of());

        PluginValidator.ValidationResult result = validator.validate(List.of(
                external("z.jar", enabled),
                external("a.jar", disabled)), config);

        assertThat(result.plugins()).extracting(plugin -> plugin.descriptor().id())
                .containsExactly("enabled");
        assertThat(disabledCalls).hasValue(0);
        assertThat(enabledCalls).hasValue(1);
        assertThat(result.reports()).filteredOn(report -> "disabled".equals(report.pluginId()))
                .singleElement().satisfies(report -> {
                    assertThat(report.state()).isEqualTo(PluginState.VALIDATED);
                    assertThat(report.enabled()).isFalse();
                });
    }

    @Test
    void rejectsIncompatibleApiAndSortsEnabledPluginsDeterministically() {
        YierLoomPlugin incompatible = new RuntimePlugin("future", 0, "2.0.0");

        PluginValidator.ValidationResult result = validator.validate(List.of(
                external("z.jar", runtimePlugin("zeta", 20)),
                external("b.jar", runtimePlugin("same-order-b", 10)),
                external("a.jar", runtimePlugin("same-order-a", 10)),
                external("future.jar", incompatible)), config(Map.of(), Map.of()));

        assertThat(result.plugins()).extracting(plugin -> plugin.descriptor().id())
                .containsExactly("same-order-a", "same-order-b", "zeta");
        assertThat(result.reports()).filteredOn(report -> "future".equals(report.pluginId()))
                .extracting(PluginReport::reasonCode).containsExactly("API_INCOMPATIBLE");
    }

    private static YierLoomPlugin countingInstrumentation(
            String id,
            int order,
            AtomicInteger calls
    ) {
        return new InstrumentedPlugin(id, order) {
            @Override
            public List<InstrumentationModule> instrumentations(PluginConfig config) {
                calls.incrementAndGet();
                return List.of(module(id + "-module"));
            }
        };
    }

    private static YierLoomPlugin plugin(String id, int order) {
        return new BasePlugin(id, order, "1.0.0");
    }

    private static YierLoomPlugin runtimePlugin(String id, int order) {
        return new RuntimePlugin(id, order, "1.0.0");
    }

    private static YierLoomPlugin instrumentationPlugin(
            String id,
            int order,
            List<InstrumentationModule> modules
    ) {
        return new InstrumentedPlugin(id, order) {
            @Override
            public List<InstrumentationModule> instrumentations(PluginConfig config) {
                return modules;
            }
        };
    }

    private static YierLoomPlugin combinedPlugin(
            String id,
            int order,
            List<InstrumentationModule> modules
    ) {
        return new CombinedPlugin(id, order, modules);
    }

    private static InstrumentationModule module(String id, String... helpers) {
        return new InstrumentationModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<? extends TypeInstrumentation> typeInstrumentations() {
                return List.of();
            }

            @Override
            public Set<String> helperClassNames() {
                return Set.of(helpers);
            }
        };
    }

    private static DiscoveredPlugin builtIn(YierLoomPlugin plugin) {
        return new DiscoveredPlugin(plugin, PluginSource.BUILT_IN, Path.of("built-in", plugin.getClass().getName()));
    }

    private static DiscoveredPlugin external(String path, YierLoomPlugin plugin) {
        return new DiscoveredPlugin(plugin, PluginSource.EXTERNAL, Path.of(path).toAbsolutePath().normalize());
    }

    private static YierLoomConfig config(
            Map<String, PluginConfig> pluginConfigs,
            Map<String, ConfigOrigin> origins
    ) {
        return new YierLoomConfig(
                true,
                Optional.empty(),
                16,
                "test-service",
                pluginConfigs,
                origins,
                List.of());
    }

    private static class BasePlugin implements YierLoomPlugin {
        private final PluginDescriptor descriptor;

        private BasePlugin(String id, int order, String apiVersion) {
            descriptor = new PluginDescriptor(id, id, "1.0.0", apiVersion, true, order);
        }

        @Override
        public PluginDescriptor descriptor() {
            return descriptor;
        }
    }

    private static final class RuntimePlugin extends BasePlugin implements RuntimeCapability {
        private RuntimePlugin(String id, int order, String apiVersion) {
            super(id, order, apiVersion);
        }

        @Override
        public void start(PluginRuntimeContext context) {
        }

        @Override
        public void stop() {
        }
    }

    private abstract static class InstrumentedPlugin extends BasePlugin implements InstrumentationCapability {
        private InstrumentedPlugin(String id, int order) {
            super(id, order, "1.0.0");
        }
    }

    private static final class CombinedPlugin extends BasePlugin
            implements RuntimeCapability, InstrumentationCapability {
        private final List<InstrumentationModule> modules;

        private CombinedPlugin(String id, int order, List<InstrumentationModule> modules) {
            super(id, order, "1.0.0");
            this.modules = new ArrayList<>(modules);
        }

        @Override
        public void start(PluginRuntimeContext context) {
            context.scheduler().scheduleWithFixedDelay(
                    "task", Duration.ofDays(1), Duration.ofDays(1), () -> { });
        }

        @Override
        public void stop() {
        }

        @Override
        public List<InstrumentationModule> instrumentations(PluginConfig config) {
            return modules;
        }
    }

}
