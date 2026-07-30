package com.nowcoder.yierloom.bootstrap;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.core.YierLoomEngine;
import com.nowcoder.yierloom.sdk.InstrumentationModule;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class YierLoomEngineClassLoaderTest {
    private static final String PLUGIN_SERVICE =
            "META-INF/services/com.nowcoder.yierloom.api.YierLoomPlugin";

    @TempDir
    Path tempDir;

    @Test
    void loadsCoreSdkAndByteBuddyChildFirstButDelegatesApiAndBootstrap() throws Exception {
        try (YierLoomEngineClassLoader loader = new YierLoomEngineClassLoader(
                locationOf(InstrumentationModule.class),
                locationOf(YierLoomEngine.class),
                locationOf(ByteBuddy.class),
                getClass().getClassLoader())) {
            assertThat(loader.loadClass(YierLoomEngine.class.getName()).getClassLoader())
                    .isSameAs(loader);
            assertThat(loader.loadClass(InstrumentationModule.class.getName()).getClassLoader())
                    .isSameAs(loader);
            assertThat(loader.loadClass(ByteBuddy.class.getName()).getClassLoader())
                    .isSameAs(loader);
            assertThat(loader.loadClass(YierLoomPlugin.class.getName()))
                    .isSameAs(YierLoomPlugin.class);
            assertThat(loader.loadClass(YierLoomAgent.class.getName()))
                    .isSameAs(YierLoomAgent.class);
        }
    }

    @Test
    void exposesOnlyEngineOwnedPluginServiceResources() throws Exception {
        Path parentService = serviceJar("parent.jar", "example.ParentPlugin");
        Path engineService = serviceJar("engine.jar", "example.EnginePlugin");

        try (URLClassLoader parent = new URLClassLoader(
                new java.net.URL[]{parentService.toUri().toURL()}, getClass().getClassLoader());
             YierLoomEngineClassLoader loader = new YierLoomEngineClassLoader(
                     locationOf(InstrumentationModule.class),
                     engineService,
                     locationOf(ByteBuddy.class),
                     parent)) {
            List<String> declarations = new ArrayList<>();
            var resources = loader.getResources(PLUGIN_SERVICE);
            while (resources.hasMoreElements()) {
                try (InputStream input = resources.nextElement().openStream()) {
                    declarations.add(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }

            assertThat(declarations).containsExactly("example.EnginePlugin\n");
            assertThat(loader.getResource(PLUGIN_SERVICE)).isNotNull();
        }
    }

    private Path serviceJar(String name, String provider) throws Exception {
        Path jar = tempDir.resolve(name);
        try (OutputStream output = Files.newOutputStream(jar);
             JarOutputStream archive = new JarOutputStream(output)) {
            archive.putNextEntry(new JarEntry(PLUGIN_SERVICE));
            archive.write((provider + "\n").getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        return jar;
    }

    private static Path locationOf(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
