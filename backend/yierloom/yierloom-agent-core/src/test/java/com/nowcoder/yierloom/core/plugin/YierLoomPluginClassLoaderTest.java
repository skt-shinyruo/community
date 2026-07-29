package com.nowcoder.yierloom.core.plugin;

import java.nio.file.Path;

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
}
