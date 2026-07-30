package com.nowcoder.yierloom.bootstrap;

import java.lang.instrument.Instrumentation;

public final class SystemApiPreloaderAgent {
    private SystemApiPreloaderAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation)
            throws ClassNotFoundException {
        Class<?> type = Class.forName(
                "com.nowcoder.yierloom.api.DiagnosticEvent",
                false,
                ClassLoader.getSystemClassLoader());
        String owner = type.getClassLoader() == ClassLoader.getSystemClassLoader()
                ? "system"
                : String.valueOf(type.getClassLoader());
        System.out.println("preloaded-api-loader=" + owner);
    }
}
