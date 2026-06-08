package com.nowcoder.observability.runtimediagnostics.core;

public interface Probe {

    String name();

    void start(ProbeContext context);

    default void stop() {
    }
}
