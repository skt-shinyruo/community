package com.nowcoder.yierloom.core.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class YierLoomPluginClassLoader extends URLClassLoader {
    private static final List<String> PARENT_FIRST = List.of(
            "java.", "javax.", "jdk.", "sun.",
            "com.nowcoder.yierloom.api.",
            "com.nowcoder.yierloom.sdk.",
            "net.bytebuddy.");
    private static final List<String> FORBIDDEN = List.of(
            "com.nowcoder.yierloom.core.",
            "com.nowcoder.yierloom.bootstrap.",
            "com.nowcoder.yierloom.plugins.");
    private static final List<String> PARENT_FIRST_RESOURCES = PARENT_FIRST.stream()
            .map(prefix -> prefix.replace('.', '/'))
            .toList();
    private static final List<String> FORBIDDEN_RESOURCES = FORBIDDEN.stream()
            .map(prefix -> prefix.replace('.', '/'))
            .toList();

    static {
        registerAsParallelCapable();
    }

    public YierLoomPluginClassLoader(Path jar, ClassLoader parent) throws IOException {
        super(new URL[]{jar.toRealPath().toUri().toURL()}, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if (FORBIDDEN.stream().anyMatch(name::startsWith)) {
                throw new ClassNotFoundException("YierLoom internal package is not visible");
            }
            if (PARENT_FIRST.stream().anyMatch(name::startsWith)) {
                return super.loadClass(name, resolve);
            }
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                try {
                    type = findClass(name);
                } catch (ClassNotFoundException missingInPlugin) {
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
        if (matches(name, FORBIDDEN_RESOURCES)) {
            return null;
        }
        if (matches(name, PARENT_FIRST_RESOURCES)) {
            return super.getResource(name);
        }
        URL resource = findResource(name);
        return resource == null ? super.getResource(name) : resource;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (matches(name, FORBIDDEN_RESOURCES)) {
            return Collections.emptyEnumeration();
        }
        if (matches(name, PARENT_FIRST_RESOURCES)) {
            return super.getResources(name);
        }
        Set<URL> resources = new LinkedHashSet<>();
        addAll(resources, findResources(name));
        addAll(resources, super.getResources(name));
        return Collections.enumeration(resources);
    }

    private static boolean matches(String name, List<String> prefixes) {
        return prefixes.stream().anyMatch(name::startsWith);
    }

    private static void addAll(Set<URL> target, Enumeration<URL> resources) {
        while (resources.hasMoreElements()) {
            target.add(resources.nextElement());
        }
    }
}
