package com.nowcoder.yierloom.core.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

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
}
