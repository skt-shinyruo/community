package com.nowcoder.yierloom.core;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class BootstrapCleanup implements Runnable {
    private final Runnable delegate;
    private final AtomicReference<State> state = new AtomicReference<>(State.AVAILABLE);

    BootstrapCleanup(Runnable delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    void acceptOwnership() {
        invokeOptional("acceptOwnership");
    }

    void installShutdownHook(Runnable coreShutdown) {
        Objects.requireNonNull(coreShutdown, "coreShutdown");
        try {
            delegate.getClass()
                    .getMethod("installShutdownHook", Runnable.class)
                    .invoke(delegate, coreShutdown);
        } catch (NoSuchMethodException ignored) {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(coreShutdown, "yierloom-shutdown"));
        } catch (InvocationTargetException invocationFailure) {
            rethrow(invocationFailure.getCause() == null
                    ? invocationFailure
                    : invocationFailure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "unable to install YierLoom shutdown hook", failure);
        }
    }

    private Throwable releaseOwnership() {
        try {
            invokeOptional("releaseOwnership");
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private void invokeOptional(String methodName) {
        try {
            delegate.getClass().getMethod(methodName).invoke(delegate);
        } catch (NoSuchMethodException ignored) {
            // Unit-test and embedded callers may supply a plain Runnable.
        } catch (InvocationTargetException invocationFailure) {
            rethrow(invocationFailure.getCause() == null
                    ? invocationFailure
                    : invocationFailure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "unable to invoke bootstrap resource ownership operation", failure);
        }
    }

    @Override
    public void run() {
        if (state.compareAndSet(State.AVAILABLE, State.RUNNING)) {
            try {
                delegate.run();
                state.set(State.CLEANED);
            } catch (Throwable failure) {
                state.compareAndSet(State.RUNNING, State.AVAILABLE);
                rethrow(preferred(failure, releaseOwnership()));
            }
        }
    }

    void retain() {
        state.compareAndSet(State.AVAILABLE, State.RETAINED);
    }

    boolean retained() {
        return state.get() == State.RETAINED;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("bootstrap resource ownership operation failed", failure);
    }

    private static Throwable preferred(Throwable primary, Throwable ownershipFailure) {
        Throwable fatal = FatalFailures.find(primary);
        if (fatal != null) {
            return fatal;
        }
        fatal = FatalFailures.find(ownershipFailure);
        if (fatal != null) {
            return fatal;
        }
        return ownershipFailure == null
                ? primary
                : new CombinedFailure(primary, ownershipFailure);
    }

    static final class CombinedFailure extends RuntimeException {
        private final Throwable ownershipFailure;

        private CombinedFailure(Throwable cleanupFailure, Throwable ownershipFailure) {
            super("YierLoom cleanup and ownership release both failed", cleanupFailure, false, false);
            this.ownershipFailure = ownershipFailure;
        }

        Throwable ownershipFailure() {
            return ownershipFailure;
        }
    }

    private enum State {
        AVAILABLE,
        RUNNING,
        CLEANED,
        RETAINED
    }
}
