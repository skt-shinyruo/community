package com.nowcoder.yierloom.bootstrap;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class BootstrapFailures {
    private BootstrapFailures() {
    }

    static Throwable capture(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    static Throwable preferred(Throwable primary, Throwable cleanup) {
        Throwable fatal = fatalCause(primary);
        if (fatal != null) {
            return fatal;
        }
        fatal = fatalCause(cleanup);
        if (fatal != null) {
            return fatal;
        }
        if (primary == null) {
            return cleanup;
        }
        if (cleanup == null) {
            return primary;
        }
        return new CombinedFailure(primary, cleanup);
    }

    static Throwable fatalCause(Throwable failure) {
        if (failure == null) {
            return null;
        }
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
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

    static void rethrowFatal(Throwable failure) {
        Throwable fatal = fatalCause(failure);
        if (fatal instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (fatal instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }

    static final class CombinedFailure extends RuntimeException {
        private final Throwable cleanupFailure;

        private CombinedFailure(Throwable primary, Throwable cleanupFailure) {
            super("YierLoom operation and cleanup both failed", primary, false, false);
            this.cleanupFailure = cleanupFailure;
        }

        Throwable cleanupFailure() {
            return cleanupFailure;
        }
    }
}
