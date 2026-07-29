package com.nowcoder.yierloom.core.instrumentation;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

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
        Throwable fatal = fatalCause(failure);
        if (fatal instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (fatal instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }

    static Throwable fatalCause(Throwable failure) {
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(Objects.requireNonNull(failure, "failure"));
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current instanceof VirtualMachineError || current instanceof ThreadDeath) {
                return current;
            }
            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
            Collections.addAll(pending, current.getSuppressed());
        }
        return null;
    }
}
