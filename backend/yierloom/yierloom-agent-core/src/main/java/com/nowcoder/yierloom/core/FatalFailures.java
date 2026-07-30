package com.nowcoder.yierloom.core;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class FatalFailures {
    private FatalFailures() {
    }

    public static Throwable find(Throwable failure) {
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

    public static void rethrow(Throwable failure) {
        Throwable fatal = find(failure);
        if (fatal instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (fatal instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }
}
