package com.nowcoder.yierloom.testkit.internal;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class ContractPluginClassLoader extends URLClassLoader {
    private static final List<String> JDK_PACKAGES = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "org.ietf.jgss.", "org.w3c.dom.", "org.xml.sax.");
    private static final List<String> SHARED_PACKAGES = List.of(
            "com.nowcoder.yierloom.api.",
            "com.nowcoder.yierloom.sdk.",
            "net.bytebuddy.");
    private static final List<String> FORBIDDEN = List.of(
            "com.nowcoder.yierloom.core.",
            "com.nowcoder.yierloom.bootstrap.",
            "com.nowcoder.yierloom.plugins.");
    private static final List<String> JDK_RESOURCES = JDK_PACKAGES.stream()
            .map(prefix -> prefix.replace('.', '/'))
            .toList();
    private static final List<String> SHARED_RESOURCES = SHARED_PACKAGES.stream()
            .map(prefix -> prefix.replace('.', '/'))
            .toList();
    private static final List<String> FORBIDDEN_RESOURCES = FORBIDDEN.stream()
            .map(prefix -> prefix.replace('.', '/'))
            .toList();

    static {
        registerAsParallelCapable();
    }

    public ContractPluginClassLoader(Path pluginJar, ClassLoader parent) throws IOException {
        super(new URL[]{pluginJar.toRealPath().toUri().toURL()}, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if (matches(name, FORBIDDEN)) {
                throw new ClassNotFoundException("YierLoom internal package is not visible");
            }
            if (matches(name, JDK_PACKAGES)) {
                return ClassLoader.getPlatformClassLoader().loadClass(name);
            }
            if (matches(name, SHARED_PACKAGES)) {
                return loadFromParent(name);
            }
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                type = findClass(name);
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
        if (matches(name, JDK_RESOURCES)) {
            return ClassLoader.getPlatformClassLoader().getResource(name);
        }
        if (matches(name, SHARED_RESOURCES)) {
            return resourceFromParent(name);
        }
        return findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (matches(name, FORBIDDEN_RESOURCES)) {
            return Collections.emptyEnumeration();
        }
        if (matches(name, JDK_RESOURCES)) {
            return ClassLoader.getPlatformClassLoader().getResources(name);
        }
        if (matches(name, SHARED_RESOURCES)) {
            return resourcesFromParent(name);
        }
        return findResources(name);
    }

    private Class<?> loadFromParent(String name) throws ClassNotFoundException {
        ClassLoader parent = getParent();
        return parent == null ? Class.forName(name, false, null) : parent.loadClass(name);
    }

    private URL resourceFromParent(String name) {
        ClassLoader parent = getParent();
        return parent == null ? ClassLoader.getSystemResource(name) : parent.getResource(name);
    }

    private Enumeration<URL> resourcesFromParent(String name) throws IOException {
        ClassLoader parent = getParent();
        return parent == null ? ClassLoader.getSystemResources(name) : parent.getResources(name);
    }

    private static boolean matches(String value, List<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }
}
