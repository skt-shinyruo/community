package com.nowcoder.yierloom.core.instrumentation;

import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;

final class QuiescingTransformer extends ResettableClassFileTransformer.AbstractBase {
    private final ResettableClassFileTransformer delegate;
    private final Object monitor = new Object();
    private boolean accepting = true;
    private int inFlight;

    QuiescingTransformer(ResettableClassFileTransformer delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) throws IllegalClassFormatException {
        if (!enter()) {
            return null;
        }
        try {
            return delegate.transform(
                    module,
                    loader,
                    className,
                    classBeingRedefined,
                    protectionDomain,
                    classfileBuffer);
        } finally {
            exit();
        }
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) throws IllegalClassFormatException {
        if (!enter()) {
            return null;
        }
        try {
            return delegate.transform(
                    loader,
                    className,
                    classBeingRedefined,
                    protectionDomain,
                    classfileBuffer);
        } finally {
            exit();
        }
    }

    @Override
    public Iterator<AgentBuilder.Transformer> iterator(
            TypeDescription typeDescription,
            ClassLoader classLoader,
            JavaModule module,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain
    ) {
        return delegate.iterator(
                typeDescription, classLoader, module, classBeingRedefined, protectionDomain);
    }

    @Override
    public boolean reset(
            Instrumentation instrumentation,
            ResettableClassFileTransformer transformer,
            AgentBuilder.RedefinitionStrategy redefinitionStrategy,
            AgentBuilder.RedefinitionStrategy.DiscoveryStrategy discoveryStrategy,
            AgentBuilder.RedefinitionStrategy.BatchAllocator batchAllocator,
            AgentBuilder.RedefinitionStrategy.Listener listener
    ) {
        return delegate.reset(
                instrumentation,
                transformer,
                redefinitionStrategy,
                discoveryStrategy,
                batchAllocator,
                listener);
    }

    void closeAdmission() {
        synchronized (monitor) {
            accepting = false;
        }
    }

    boolean awaitQuiescence(long deadline) {
        synchronized (monitor) {
            while (inFlight != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    private boolean enter() {
        synchronized (monitor) {
            if (!accepting) {
                return false;
            }
            inFlight++;
            return true;
        }
    }

    private void exit() {
        synchronized (monitor) {
            inFlight--;
            if (inFlight == 0) {
                monitor.notifyAll();
            }
        }
    }
}
