package com.example.yierloom.integration;

import java.io.IOException;
import java.io.InputStream;

public final class CustomTargetClassLoader extends ClassLoader {
    public static final String TARGET_CLASS_NAME =
            "com.example.yierloom.integration.CustomLoadedTarget";

    public CustomTargetClassLoader() {
        super(ClassLoader.getSystemClassLoader());
    }

    public Class<?> loadTarget() throws ClassNotFoundException {
        return loadClass(TARGET_CLASS_NAME);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!isLocallyDefined(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (!TARGET_CLASS_NAME.equals(name)) {
                    // HelperInjector observes the miss and then defines the Helper in this loader.
                    throw new ClassNotFoundException(name);
                }
                loaded = defineTarget();
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> defineTarget() throws ClassNotFoundException {
        String resourceName = TARGET_CLASS_NAME.replace('.', '/') + ".class";
        try (InputStream input = getParent().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new ClassNotFoundException(
                        TARGET_CLASS_NAME + " bytecode is unavailable");
            }
            byte[] bytecode = input.readAllBytes();
            return defineClass(TARGET_CLASS_NAME, bytecode, 0, bytecode.length);
        } catch (IOException failure) {
            throw new ClassNotFoundException(
                    "cannot read " + TARGET_CLASS_NAME + " bytecode", failure);
        }
    }

    private static boolean isLocallyDefined(String name) {
        if (TARGET_CLASS_NAME.equals(name)) {
            return true;
        }
        return name.endsWith("Helper")
                && (name.startsWith("com.nowcoder.yierloom.plugins.")
                || name.startsWith("fixture."));
    }
}
