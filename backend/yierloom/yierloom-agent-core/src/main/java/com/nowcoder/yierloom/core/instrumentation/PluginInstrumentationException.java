package com.nowcoder.yierloom.core.instrumentation;

import java.util.Objects;

import com.nowcoder.yierloom.core.FatalFailures;

public final class PluginInstrumentationException extends RuntimeException {
    private final String pluginId;

    PluginInstrumentationException(String pluginId, Throwable cause) {
        super("YierLoom instrumentation failed for plugin "
                + Objects.requireNonNull(pluginId, "pluginId"), cause);
        this.pluginId = pluginId;
    }

    public String pluginId() {
        return pluginId;
    }

    static void rethrowIfFatal(Throwable failure) {
        FatalFailures.rethrow(Objects.requireNonNull(failure, "failure"));
    }

    static Throwable fatalCause(Throwable failure) {
        return FatalFailures.find(Objects.requireNonNull(failure, "failure"));
    }
}
