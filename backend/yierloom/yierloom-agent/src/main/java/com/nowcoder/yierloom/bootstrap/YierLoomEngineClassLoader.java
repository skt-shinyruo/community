package com.nowcoder.yierloom.bootstrap;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class YierLoomEngineClassLoader extends URLClassLoader {
    private static final List<String> CHILD_FIRST_CLASSES = List.of(
            "com.nowcoder.yierloom.core.",
            "com.nowcoder.yierloom.sdk.",
            "com.nowcoder.yierloom.plugins.",
            "net.bytebuddy.");
    private static final List<String> CHILD_FIRST_RESOURCES = CHILD_FIRST_CLASSES.stream()
            .map(prefix -> prefix.replace('.', '/'))
            .toList();
    private static final String PLUGIN_SERVICE =
            "META-INF/services/com.nowcoder.yierloom.api.YierLoomPlugin";

    static {
        registerAsParallelCapable();
    }

    public YierLoomEngineClassLoader(
            Path sdkJar,
            Path coreJar,
            Path byteBuddyJar,
            ClassLoader parent
    ) throws IOException {
        super(urls(sdkJar, coreJar, byteBuddyJar), Objects.requireNonNull(parent, "parent"));
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!matches(name, CHILD_FIRST_CLASSES)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                try {
                    type = findClass(name);
                } catch (ClassNotFoundException missingInEngine) {
                    type = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }
    }

    @Override
    public URL getResource(String name) {
        if (PLUGIN_SERVICE.equals(name)) {
            return findResource(name);
        }
        if (!childFirstResource(name)) {
            return super.getResource(name);
        }
        URL resource = findResource(name);
        return resource == null ? super.getResource(name) : resource;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (PLUGIN_SERVICE.equals(name)) {
            return findResources(name);
        }
        if (!childFirstResource(name)) {
            return super.getResources(name);
        }
        Set<URL> resources = new LinkedHashSet<>();
        addAll(resources, findResources(name));
        addAll(resources, super.getResources(name));
        return Collections.enumeration(resources);
    }

    private static URL[] urls(Path sdkJar, Path coreJar, Path byteBuddyJar) throws IOException {
        return new URL[]{url(sdkJar), url(coreJar), url(byteBuddyJar)};
    }

    private static URL url(Path path) throws IOException {
        return Objects.requireNonNull(path, "path").toRealPath().toUri().toURL();
    }

    private static boolean childFirstResource(String name) {
        return matches(name, CHILD_FIRST_RESOURCES);
    }

    private static boolean matches(String value, List<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }

    private static void addAll(Set<URL> target, Enumeration<URL> source) {
        while (source.hasMoreElements()) {
            target.add(source.nextElement());
        }
    }
}
