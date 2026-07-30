package com.nowcoder.yierloom.core.instrumentation;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy;

final class HelperInjector {
    private final AgentBuilder.InjectionStrategy strategy;
    private final Object lock = new Object();
    private final ReferenceQueue<ClassLoader> collectedLoaders = new ReferenceQueue<>();
    private final Map<WeakIdentityKey, Set<String>> injected = new HashMap<>();
    private final Set<String> bootstrapInjected = new HashSet<>();

    HelperInjector(AgentBuilder.InjectionStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    void inject(
            ClassLoader loader,
            ProtectionDomain protectionDomain,
            Map<String, byte[]> helpers
    ) {
        Objects.requireNonNull(helpers, "helpers");
        if (helpers.isEmpty()) {
            return;
        }
        synchronized (lock) {
            discardCollectedLoaders();
            Set<String> known = loader == null ? bootstrapInjected : knownFor(loader);
            Map<String, byte[]> missing = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> helper : helpers.entrySet()) {
                String helperName = helper.getKey();
                if (known.contains(helperName)) {
                    continue;
                }
                Class<?> visible = visibleClass(helperName, loader);
                if (visible == null) {
                    missing.put(helperName, helper.getValue());
                } else if (wasInjected(visible.getClassLoader(), helperName)) {
                    known.add(helperName);
                } else {
                    throw new IllegalStateException(
                            "Helper class is already visible: " + helperName);
                }
            }
            if (missing.isEmpty()) {
                return;
            }
            ClassInjector injector = strategy.resolve(loader, protectionDomain);
            if (loader != null && injector instanceof ClassInjector.UsingReflection) {
                injector = new ClassInjector.UsingReflection(
                        loader,
                        protectionDomain,
                        PackageDefinitionStrategy.Trivial.INSTANCE,
                        true);
            }
            injector.injectRaw(missing);
            known.addAll(missing.keySet());
        }
    }

    private static Class<?> visibleClass(String helperName, ClassLoader loader) {
        try {
            return Class.forName(helperName, false, loader);
        } catch (ClassNotFoundException expected) {
            return null;
        }
    }

    private boolean wasInjected(ClassLoader definingLoader, String helperName) {
        if (definingLoader == null) {
            return bootstrapInjected.contains(helperName);
        }
        Set<String> known = injected.get(new WeakIdentityKey(definingLoader, null));
        return known != null && known.contains(helperName);
    }

    private Set<String> knownFor(ClassLoader loader) {
        WeakIdentityKey lookup = new WeakIdentityKey(loader, null);
        Set<String> known = injected.get(lookup);
        if (known != null) {
            return known;
        }
        Set<String> created = new HashSet<>();
        injected.put(new WeakIdentityKey(loader, collectedLoaders), created);
        return created;
    }

    private void discardCollectedLoaders() {
        WeakIdentityKey collected;
        while ((collected = (WeakIdentityKey) collectedLoaders.poll()) != null) {
            injected.remove(collected);
        }
    }

    private static final class WeakIdentityKey extends WeakReference<ClassLoader> {
        private final int identityHash;

        private WeakIdentityKey(ClassLoader loader, ReferenceQueue<ClassLoader> queue) {
            super(Objects.requireNonNull(loader, "loader"), queue);
            identityHash = System.identityHashCode(loader);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WeakIdentityKey key)) {
                return false;
            }
            ClassLoader loader = get();
            return loader != null && loader == key.get();
        }
    }
}
