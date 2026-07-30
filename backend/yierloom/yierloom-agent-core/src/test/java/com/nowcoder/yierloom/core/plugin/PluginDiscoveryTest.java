package com.nowcoder.yierloom.core.plugin;

import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.core.config.YierLoomConfig;
import com.nowcoder.yierloom.core.event.YierLoomEventPipeline;
import com.nowcoder.yierloom.core.instrumentation.PluginInstrumentationController;
import com.nowcoder.yierloom.core.runtime.ManagedSchedulerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginDiscoveryTest {
    private static final AtomicInteger BUILT_IN_STARTS = new AtomicInteger();

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetBuiltIn() {
        BUILT_IN_STARTS.set(0);
    }

    @Test
    void invalidExternalDirectoriesStillReturnAnActivatableBuiltIn() throws Exception {
        Path serviceJar = PluginJarFixture.serviceOnlyJar(
                tempDir.resolve("engine"), "built-in.jar", BuiltInPlugin.class.getName());
        try (URLClassLoader engineLoader = new URLClassLoader(
                new java.net.URL[]{serviceJar.toUri().toURL()}, getClass().getClassLoader())) {
            Path missing = tempDir.resolve("missing");
            PluginDiscovery.Result missingResult = new PluginDiscovery().discover(config(missing), engineLoader);

            assertThat(missingResult.plugins()).extracting(plugin -> plugin.descriptor().id())
                    .containsExactly("built-in");
            assertThat(missingResult.issues()).extracting(PluginIssue::reasonCode)
                    .containsExactly("EXTERNAL_DIRECTORY_INVALID");
            assertActivatable(missingResult);

            Path regularFile = Files.writeString(tempDir.resolve("not-a-directory"), "value");
            PluginDiscovery.Result fileResult = new PluginDiscovery().discover(config(regularFile), engineLoader);
            assertThat(fileResult.plugins()).extracting(plugin -> plugin.descriptor().id())
                    .containsExactly("built-in");
            assertThat(fileResult.issues()).extracting(PluginIssue::reasonCode)
                    .containsExactly("EXTERNAL_DIRECTORY_INVALID");
            assertActivatable(fileResult);
        }
    }

    @Test
    void ordersOnlyJarCandidatesByNormalizedAbsolutePath() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        Path zeta = PluginJarFixture.providerJar(
                plugins, "zeta.JAR", "fixture.external.ZetaPlugin", providerSource("ZetaPlugin", "zeta"));
        Path alpha = PluginJarFixture.providerJar(
                plugins, "alpha.jar", "fixture.external.AlphaPlugin", providerSource("AlphaPlugin", "alpha"));
        Files.writeString(plugins.resolve("ignored.txt"), "ignored");

        PluginDiscovery.Result result = new PluginDiscovery().discover(config(plugins), getClass().getClassLoader());

        assertThat(result.plugins()).extracting(plugin -> plugin.sourcePath().toString())
                .containsExactly(
                        alpha.toAbsolutePath().normalize().toString(),
                        zeta.toAbsolutePath().normalize().toString());
        assertThat(result.issues()).isEmpty();
        assertThat(result.externalLoaders()).hasSize(2);
        close(result);
    }

    @Test
    void corruptJarFailsOpenAndLeavesBuiltInsActivatable() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        PluginJarFixture.corruptJar(plugins, "broken.jar");
        Path serviceJar = PluginJarFixture.serviceOnlyJar(
                tempDir.resolve("engine"), "built-in.jar", BuiltInPlugin.class.getName());

        try (URLClassLoader engineLoader = new URLClassLoader(
                new java.net.URL[]{serviceJar.toUri().toURL()}, getClass().getClassLoader())) {
            PluginDiscovery.Result result = new PluginDiscovery().discover(config(plugins), engineLoader);

            assertThat(result.plugins()).extracting(plugin -> plugin.descriptor().id())
                    .containsExactly("built-in");
            assertThat(result.issues()).extracting(PluginIssue::reasonCode)
                    .containsExactly("CANDIDATE_INVALID");
            assertActivatable(result);
        }
    }

    @Test
    void rejectsZeroOrMultipleProviderDeclarations() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        PluginJarFixture.jarWithoutProvider(plugins, "none.jar");
        PluginJarFixture.jarWithProviders(
                plugins,
                "two.jar",
                Map.of(
                        "fixture.external.OnePlugin", providerSource("OnePlugin", "one"),
                        "fixture.external.TwoPlugin", providerSource("TwoPlugin", "two")),
                List.of("fixture.external.OnePlugin", "fixture.external.TwoPlugin"));

        PluginDiscovery.Result result = new PluginDiscovery().discover(config(plugins), getClass().getClassLoader());

        assertThat(result.plugins()).isEmpty();
        assertThat(result.issues()).extracting(PluginIssue::reasonCode)
                .containsExactly("PROVIDER_DECLARATION_INVALID", "PROVIDER_DECLARATION_INVALID");
        assertThat(result.externalLoaders()).isEmpty();
    }

    @Test
    void rejectsJarsThatBundleSharedOrInternalClasses() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        List<String> forbiddenEntries = List.of(
                "com/nowcoder/yierloom/api/Copy.class",
                "com/nowcoder/yierloom/sdk/Copy.class",
                "net/bytebuddy/Copy.class",
                "com/nowcoder/yierloom/core/Copy.class",
                "com/nowcoder/yierloom/bootstrap/Copy.class",
                "com/nowcoder/yierloom/plugins/Copy.class");
        for (int index = 0; index < forbiddenEntries.size(); index++) {
            PluginJarFixture.jarWithForbiddenEntries(
                    plugins, "bundled-" + index + ".jar", List.of(forbiddenEntries.get(index)));
        }

        PluginDiscovery.Result result = new PluginDiscovery().discover(config(plugins), getClass().getClassLoader());

        assertThat(result.plugins()).isEmpty();
        assertThat(result.issues()).extracting(PluginIssue::reasonCode)
                .containsOnly("BUNDLED_FORBIDDEN_CLASS")
                .hasSize(forbiddenEntries.size());
        assertThat(result.externalLoaders()).isEmpty();
    }

    @Test
    void rejectsExternalServiceDeclarationThatNamesAParentOwnedProvider() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        String providerName = "fixture.parent.ParentOwnedPlugin";
        Path parentJar = PluginJarFixture.classesOnlyJar(
                tempDir.resolve("engine"),
                "parent-classes.jar",
                providerName,
                providerSource("fixture.parent", "ParentOwnedPlugin", "parent-owned"));
        PluginJarFixture.serviceOnlyJar(plugins, "borrowed-provider.jar", providerName);

        try (URLClassLoader engineLoader = new URLClassLoader(
                new java.net.URL[]{parentJar.toUri().toURL()}, getClass().getClassLoader())) {
            PluginDiscovery.Result result = new PluginDiscovery().discover(config(plugins), engineLoader);

            assertThat(result.plugins()).isEmpty();
            assertThat(result.issues()).extracting(PluginIssue::reasonCode)
                    .containsExactly("PROVIDER_TYPE_INVALID");
            assertThat(result.externalLoaders()).isEmpty();
        }
    }

    @Test
    void constructorFailureDoesNotPreventLaterCandidates() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        PluginJarFixture.providerJar(plugins, "a-broken.jar", "fixture.external.BrokenPlugin", """
                package fixture.external;
                import com.nowcoder.yierloom.api.*;
                public final class BrokenPlugin implements YierLoomPlugin {
                    public BrokenPlugin() { throw new IllegalStateException("private constructor failure"); }
                    public PluginDescriptor descriptor() { return new PluginDescriptor("broken", "Broken", "1.0.0", "1.0.0", true, 0); }
                }
                """);
        PluginJarFixture.providerJar(
                plugins, "b-valid.jar", "fixture.external.ValidPlugin", providerSource("ValidPlugin", "valid"));

        PluginDiscovery.Result result = new PluginDiscovery().discover(config(plugins), getClass().getClassLoader());

        assertThat(result.plugins()).extracting(plugin -> plugin.descriptor().id()).containsExactly("valid");
        assertThat(result.issues()).extracting(PluginIssue::reasonCode)
                .containsExactly("PROVIDER_INSTANTIATION_FAILED");
        assertThat(result.externalLoaders()).hasSize(1);
        assertThat(result.issues().get(0).summary()).doesNotContain("private constructor failure");
        close(result);
    }

    @Test
    void parentServiceLoaderProviderIsNotCountedForAnExternalJar() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        PluginJarFixture.jarWithoutProvider(plugins, "empty.jar");
        Path serviceJar = PluginJarFixture.serviceOnlyJar(
                tempDir.resolve("engine"), "parent-provider.jar", BuiltInPlugin.class.getName());

        try (URLClassLoader engineLoader = new URLClassLoader(
                new java.net.URL[]{serviceJar.toUri().toURL()}, getClass().getClassLoader())) {
            PluginDiscovery.Result result = new PluginDiscovery().discover(config(plugins), engineLoader);

            assertThat(result.plugins()).hasSize(1);
            assertThat(result.plugins().get(0).source()).isEqualTo(PluginSource.BUILT_IN);
            assertThat(result.issues()).extracting(PluginIssue::reasonCode)
                    .containsExactly("PROVIDER_DECLARATION_INVALID");
            assertThat(result.externalLoaders()).isEmpty();
        }
    }

    @Test
    void deeplyWrappedFatalDiscoveryFailureClosesEveryOpenedExternalLoader() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        PluginJarFixture.jarWithProviders(
                plugins,
                "a-valid.jar",
                Map.of(
                        "fixture.external.ValidPlugin", providerSource("ValidPlugin", "valid"),
                        "fixture.external.Unused", """
                                package fixture.external;
                                public final class Unused { }
                                """),
                List.of("fixture.external.ValidPlugin"));
        PluginJarFixture.jarWithProviders(
                plugins,
                "b-fatal.jar",
                Map.of(
                        "fixture.external.FatalPlugin", """
                                package fixture.external;
                                import com.nowcoder.yierloom.api.*;
                                public final class FatalPlugin implements YierLoomPlugin, RuntimeCapability {
                                    public FatalPlugin() {
                                        Throwable failure = new OutOfMemoryError("fatal");
                                        for (int depth = 0; depth < 100; depth++) {
                                            failure = new IllegalStateException("wrapped", failure);
                                        }
                                        throw (RuntimeException) failure;
                                    }
                                    public PluginDescriptor descriptor() {
                                        return new PluginDescriptor("fatal", "Fatal", "1.0.0", "1.0.0", true, 0);
                                    }
                                    public void start(PluginRuntimeContext context) { }
                                    public void stop() { }
                                }
                                """,
                        "fixture.external.Unused", """
                                package fixture.external;
                                public final class Unused { }
                                """),
                List.of("fixture.external.FatalPlugin"));
        List<YierLoomPluginClassLoader> opened = new ArrayList<>();
        PluginDiscovery discovery = new PluginDiscovery((jar, parent) -> {
            YierLoomPluginClassLoader loader = new YierLoomPluginClassLoader(jar, parent);
            opened.add(loader);
            return loader;
        });

        assertThatThrownBy(() -> discovery.discover(config(plugins), getClass().getClassLoader()))
                .isInstanceOf(OutOfMemoryError.class);

        assertThat(opened).hasSize(2);
        for (YierLoomPluginClassLoader loader : opened) {
            assertThatThrownBy(() -> loader.loadClass("fixture.external.Unused"))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    private void assertActivatable(PluginDiscovery.Result discovery) {
        int startsBeforeActivation = BUILT_IN_STARTS.get();
        YierLoomConfig config = config(null);
        PluginValidator.ValidationResult validation = new PluginValidator().validate(discovery.plugins(), config);
        YierLoomEventPipeline pipeline = pipeline();
        ManagedSchedulerRegistry schedulers = new ManagedSchedulerRegistry();
        PluginLifecycleManager manager = new PluginLifecycleManager(
                config,
                pipeline,
                schedulers,
                new NoOpInstrumentationController(),
                discovery.externalLoaders(),
                Clock.systemUTC());
        try {
            manager.activate(validation.plugins());
            assertThat(manager.activePluginIds()).containsExactly("built-in");
            assertThat(BUILT_IN_STARTS).hasValue(startsBeforeActivation + 1);
        } finally {
            manager.stopRuntimesInReverseOrder();
            manager.cancelManagedTasks();
            manager.removeTransformers();
            manager.closeExternalLoaders();
        }
    }

    private static void close(PluginDiscovery.Result result) throws Exception {
        for (YierLoomPluginClassLoader loader : result.externalLoaders()) {
            loader.close();
        }
    }

    private static String providerSource(String simpleName, String id) {
        return providerSource("fixture.external", simpleName, id);
    }

    private static String providerSource(String packageName, String simpleName, String id) {
        return """
                package %s;
                import com.nowcoder.yierloom.api.*;
                public final class %s implements YierLoomPlugin, RuntimeCapability {
                    public PluginDescriptor descriptor() { return new PluginDescriptor("%s", "%s", "1.0.0", "1.0.0", true, 0); }
                    public void start(PluginRuntimeContext context) { }
                    public void stop() { }
                }
                """.formatted(packageName, simpleName, id, simpleName);
    }

    private static YierLoomConfig config(Path pluginDirectory) {
        return new YierLoomConfig(
                true,
                Optional.ofNullable(pluginDirectory),
                8,
                "test-service",
                Map.of(),
                Map.of(),
                List.of());
    }

    private static YierLoomEventPipeline pipeline() {
        return new YierLoomEventPipeline(
                8,
                "test-service",
                Clock.systemUTC(),
                new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8));
    }

    public static final class BuiltInPlugin implements YierLoomPlugin, RuntimeCapability {
        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor("built-in", "Built In", "1.0.0", "1.0.0", true, 0);
        }

        @Override
        public void start(PluginRuntimeContext context) {
            BUILT_IN_STARTS.incrementAndGet();
        }

        @Override
        public void stop() {
        }
    }

    private static final class NoOpInstrumentationController implements PluginInstrumentationController {
        @Override
        public void install(ValidatedPlugin plugin) {
        }

        @Override
        public void removePlugin(String pluginId) {
        }

        @Override
        public void removeAll() {
        }
    }
}
