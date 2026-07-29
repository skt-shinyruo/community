package com.nowcoder.yierloom.core.plugin;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;

import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.sdk.InstrumentationCapability;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YierLoomPluginClassLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void sharesContractsButLoadsPrivateDependenciesChildFirst() throws Exception {
        Path jar = PluginJarFixture.jarWithPrivateVersion(tempDir, "plugin-value");

        try (YierLoomPluginClassLoader loader = new YierLoomPluginClassLoader(
                jar, getClass().getClassLoader())) {
            assertThat(loader.loadClass(YierLoomPlugin.class.getName()))
                    .isSameAs(YierLoomPlugin.class);
            assertThat(loader.loadClass(InstrumentationCapability.class.getName()))
                    .isSameAs(InstrumentationCapability.class);
            assertThat(loader.loadClass(ByteBuddy.class.getName()))
                    .isSameAs(ByteBuddy.class);
            assertThat(loader.loadClass("fixture.privatecopy.Version")
                    .getMethod("value").invoke(null)).isEqualTo("plugin-value");
            assertThatThrownBy(() -> loader.loadClass("com.nowcoder.yierloom.core.YierLoomEngine"))
                    .isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> loader.loadClass("com.nowcoder.yierloom.bootstrap.YierLoomAgent"))
                    .isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> loader.loadClass("com.nowcoder.yierloom.plugins.method.MethodPlugin"))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void resolvesPrivateResourcesChildFirst() throws Exception {
        String resourceName = "fixture/privatecopy/value.txt";
        Path parentJar = PluginJarFixture.resourceOnlyJar(
                tempDir.resolve("parent"), "parent.jar", resourceName, "parent-value");
        Path pluginJar = PluginJarFixture.resourceOnlyJar(
                tempDir.resolve("plugin"), "plugin.jar", resourceName, "plugin-value");

        try (URLClassLoader parent = new URLClassLoader(
                new URL[]{parentJar.toUri().toURL()}, getClass().getClassLoader());
             YierLoomPluginClassLoader loader = new YierLoomPluginClassLoader(pluginJar, parent)) {
            assertThat(read(loader.getResourceAsStream(resourceName))).isEqualTo("plugin-value");
            assertThat(Collections.list(loader.getResources(resourceName)))
                    .extracting(YierLoomPluginClassLoaderTest::read)
                    .containsExactly("plugin-value", "parent-value");
        }
    }

    private static String read(URL resource) {
        try (InputStream input = resource.openStream()) {
            return read(input);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static String read(InputStream input) throws Exception {
        assertThat(input).isNotNull();
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}
