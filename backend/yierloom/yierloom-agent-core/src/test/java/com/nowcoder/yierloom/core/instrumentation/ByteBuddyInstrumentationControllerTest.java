package com.nowcoder.yierloom.core.instrumentation;

import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.time.Clock;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.core.instrumentation.fixture.InjectedHelper;
import com.nowcoder.yierloom.core.plugin.DiscoveredPlugin;
import com.nowcoder.yierloom.core.plugin.PluginSource;
import com.nowcoder.yierloom.core.plugin.ValidatedPlugin;
import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByteBuddyInstrumentationControllerTest {

    @Test
    void installsOneTransformerPerModuleAndRemovesOnlyTheSelectedPluginInReverseOrder() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        controller.install(validatedPlugin("alpha", modules("one", "two")));
        controller.install(validatedPlugin("beta", modules("three")));

        assertThat(jvm.addedTransformers()).hasSize(3);
        controller.removePlugin("alpha");

        assertThat(jvm.removedTransformers()).containsExactly(
                jvm.addedTransformers().get(1), jvm.addedTransformers().get(0));
        assertThat(controller.installedModuleIds("alpha")).isEmpty();
        assertThat(controller.installedModuleIds("beta")).containsExactly("three");
    }

    @Test
    void rejectsHelperBinaryNameUsedByTwoEnabledModulesBeforeInstallingAnything() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        String helper = "fixture.SharedHelper";

        assertThatThrownBy(() -> controller.install(validatedPlugin("alpha", List.of(
                module("one", Set.of(helper), List.of()),
                module("two", Set.of(helper), List.of())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(helper);
        assertThat(jvm.addedTransformers()).isEmpty();
    }

    @Test
    void reservesHelperNamesAcrossInstalledPluginsAndReleasesThemOnRemoval() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        String helper = InjectedHelper.class.getName();
        controller.install(validatedPlugin(
                "alpha", List.of(module("one", Set.of(helper), List.of()))));

        assertThatThrownBy(() -> controller.install(validatedPlugin(
                "beta", List.of(module("two", Set.of(helper), List.of())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(helper);

        controller.removePlugin("alpha");
        controller.install(validatedPlugin(
                "beta", List.of(module("two", Set.of(helper), List.of()))));
        assertThat(controller.installedModuleIds("beta")).containsExactly("two");
    }

    @Test
    void transformerFailureLeavesTargetBytesUsableAndReportsOnlyStableContext() throws Exception {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        List<String> reports = new ArrayList<>();
        ByteBuddyInstrumentationController controller = controller(jvm, reports);
        TypeInstrumentation failing = typeInstrumentation(
                ElementMatchers.named(UnmodifiedTarget.class.getName()),
                (builder, type, loader, module, domain) -> {
                    throw new IllegalStateException("token=private-transformer-data");
                });
        controller.install(validatedPlugin(
                "alpha", List.of(module("failing", Set.of(), List.of(failing)))));

        byte[] original = classBytes(UnmodifiedTarget.class);
        byte[] transformed = jvm.transform(UnmodifiedTarget.class, original);

        assertThat(defineInIsolatedLoader(transformed == null ? original : transformed).getName())
                .isEqualTo(UnmodifiedTarget.class.getName());
        assertThat(reports).anySatisfy(report -> assertThat(report)
                .contains("plugin=alpha", "module=failing", "stage=transformer")
                .doesNotContain("private-transformer-data"));
    }

    @Test
    void matcherFailureLeavesTargetBytesUsableAndIsReportedByTheModuleListener() throws Exception {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        List<String> reports = new ArrayList<>();
        ByteBuddyInstrumentationController controller = controller(jvm, reports);
        ElementMatcher<TypeDescription> failingMatcher = new ElementMatcher.Junction.AbstractBase<>() {
            @Override
            public boolean matches(TypeDescription target) {
                throw new IllegalStateException("token=private-matcher-data");
            }
        };
        TypeInstrumentation failing = typeInstrumentation(
                failingMatcher,
                (builder, type, loader, module, domain) -> builder);
        controller.install(validatedPlugin(
                "alpha", List.of(module("failing", Set.of(), List.of(failing)))));

        byte[] original = classBytes(UnmodifiedTarget.class);
        byte[] transformed = jvm.transform(UnmodifiedTarget.class, original);

        assertThat(transformed == null || java.util.Arrays.equals(transformed, original)).isTrue();
        assertThat(reports).anySatisfy(report -> assertThat(report)
                .contains("plugin=alpha", "module=failing", "stage=byte-buddy")
                .doesNotContain("private-matcher-data"));
    }

    @Test
    void helperInjectionFailureLeavesTargetUsableAndSkipsThePluginTransformer() throws Exception {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        List<String> reports = new ArrayList<>();
        ByteBuddyInstrumentationController controller = new ByteBuddyInstrumentationController(
                jvm,
                (loader, domain) -> { throw new IllegalStateException("token=private-injection-data"); },
                new TransformationErrorReporter(Clock.systemUTC(), reports::add));
        int[] transformerCalls = {0};
        TypeInstrumentation type = typeInstrumentation(
                ElementMatchers.named(UnmodifiedTarget.class.getName()),
                (builder, description, loader, module, domain) -> {
                    transformerCalls[0]++;
                    return builder;
                });
        controller.install(validatedPlugin("alpha", List.of(module(
                "helpers", Set.of(InjectedHelper.class.getName()), List.of(type)))));

        byte[] original = classBytes(UnmodifiedTarget.class);
        byte[] transformed = jvm.transform(UnmodifiedTarget.class, original);

        assertThat(defineInIsolatedLoader(transformed == null ? original : transformed).getName())
                .isEqualTo(UnmodifiedTarget.class.getName());
        assertThat(transformerCalls[0]).isZero();
        assertThat(reports).anySatisfy(report -> assertThat(report)
                .contains("plugin=alpha", "module=helpers", "stage=helper-injection")
                .doesNotContain("private-injection-data"));
    }

    @Test
    void evaluatesContributedTransformersForBootstrapLoadedTypes() throws Exception {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        AtomicInteger calls = new AtomicInteger();
        TypeInstrumentation bootstrapType = typeInstrumentation(
                ElementMatchers.named(String.class.getName()),
                (builder, description, loader, module, domain) -> {
                    assertThat(loader).isNull();
                    calls.incrementAndGet();
                    return builder;
                });
        controller.install(validatedPlugin(
                "alpha", List.of(module("bootstrap", Set.of(), List.of(bootstrapType)))));

        jvm.transform(String.class, classBytes(String.class));

        assertThat(calls).hasValue(1);
    }

    @Test
    void partialInstallationFailureRemovesAlreadyInstalledModules() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        jvm.failAddCall(2);
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());

        assertThatThrownBy(() -> controller.install(validatedPlugin("alpha", modules("one", "two"))))
                .isInstanceOf(PluginInstrumentationException.class);

        assertThat(jvm.addedTransformers()).hasSize(1);
        assertThat(jvm.removedTransformers()).endsWith(jvm.addedTransformers().get(0));
        assertThat(controller.installedModuleIds("alpha")).isEmpty();
    }

    @Test
    void wrappedVmFatalInstallationFailureStillRemovesAlreadyInstalledModules() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        jvm.failAddCall(2, new IllegalStateException(
                "wrapped fatal", new OutOfMemoryError("fatal")));
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());

        assertThatThrownBy(() -> controller.install(validatedPlugin("alpha", modules("one", "two"))))
                .isInstanceOf(OutOfMemoryError.class);

        assertThat(jvm.addedTransformers()).hasSize(1);
        assertThat(jvm.removedTransformers()).endsWith(jvm.addedTransformers().get(0));
        assertThat(controller.installedModuleIds("alpha")).isEmpty();
    }

    @Test
    void wrappedVmFatalFromPluginPreflightIsRethrownBeforeInstallation() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        InstrumentationModule module = new InstrumentationModule() {
            @Override
            public String id() {
                return "fatal";
            }

            @Override
            public List<? extends TypeInstrumentation> typeInstrumentations() {
                return List.of();
            }

            @Override
            public Set<String> helperClassNames() {
                throw new IllegalStateException(
                        "wrapped fatal", new OutOfMemoryError("fatal"));
            }
        };

        assertThatThrownBy(() -> controller.install(validatedPlugin("alpha", List.of(module))))
                .isInstanceOf(OutOfMemoryError.class);
        assertThat(jvm.addedTransformers()).isEmpty();
    }

    @Test
    void failedRemovalAttemptsEveryTransformerAndCanRetryTheRemainingHandle() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        controller.install(validatedPlugin("alpha", modules("one", "two")));
        ClassFileTransformer first = jvm.addedTransformers().get(0);
        ClassFileTransformer second = jvm.addedTransformers().get(1);
        jvm.failRemoval(second, 1);

        assertThatThrownBy(controller::removeAll)
                .isInstanceOf(PluginInstrumentationException.class);
        assertThat(jvm.removedTransformers()).containsExactly(second, first);
        assertThat(controller.installedModuleIds("alpha")).containsExactly("two");

        jvm.clearRemovedTransformers();
        controller.removeAll();
        assertThat(jvm.removedTransformers()).containsExactly(second);
        assertThat(controller.installedModuleIds("alpha")).isEmpty();
    }

    @Test
    void falseRemovalResultRetainsTheHandleUntilARetrySucceeds() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        controller.install(validatedPlugin("alpha", modules("one")));
        ClassFileTransformer transformer = jvm.addedTransformers().get(0);
        jvm.returnFalseOnRemoval(transformer, 1);

        assertThatThrownBy(controller::removeAll)
                .isInstanceOf(PluginInstrumentationException.class);
        assertThat(controller.installedModuleIds("alpha")).containsExactly("one");

        controller.removeAll();
        assertThat(controller.installedModuleIds("alpha")).isEmpty();
    }

    @Test
    void removalWaitsForInFlightTransformationAndLateCallbacksSkipPluginCode() throws Exception {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        CountDownLatch transformerEntered = new CountDownLatch(1);
        CountDownLatch releaseTransformer = new CountDownLatch(1);
        AtomicInteger transformerCalls = new AtomicInteger();
        TypeInstrumentation blocking = typeInstrumentation(
                ElementMatchers.named(UnmodifiedTarget.class.getName()),
                (builder, type, loader, module, domain) -> {
                    transformerCalls.incrementAndGet();
                    transformerEntered.countDown();
                    try {
                        releaseTransformer.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted", interrupted);
                    }
                    return builder;
                });
        controller.install(validatedPlugin(
                "alpha", List.of(module("blocking", Set.of(), List.of(blocking)))));
        byte[] original = classBytes(UnmodifiedTarget.class);
        AtomicReference<Throwable> transformFailure = new AtomicReference<>();
        Thread transformation = new Thread(() -> {
            try {
                jvm.transform(UnmodifiedTarget.class, original);
            } catch (Throwable failure) {
                transformFailure.set(failure);
            }
        });
        CountDownLatch removalFinished = new CountDownLatch(1);
        AtomicReference<Throwable> removalFailure = new AtomicReference<>();
        Thread removal = new Thread(() -> {
            try {
                controller.removeAll();
            } catch (Throwable failure) {
                removalFailure.set(failure);
            } finally {
                removalFinished.countDown();
            }
        });

        transformation.start();
        assertThat(transformerEntered.await(2, TimeUnit.SECONDS)).isTrue();
        removal.start();
        try {
            assertThat(removalFinished.await(200, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseTransformer.countDown();
            transformation.join(2_000);
            removal.join(2_000);
        }

        assertThat(transformFailure.get()).isNull();
        assertThat(removalFailure.get()).isNull();
        assertThat(removalFinished.getCount()).isZero();
        assertThat(controller.installedModuleIds("alpha")).isEmpty();
        jvm.transform(UnmodifiedTarget.class, original);
        assertThat(transformerCalls).hasValue(1);
    }

    @Test
    void repeatedRemovalFailureInstanceDoesNotInterruptRemainingCleanup() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        controller.install(validatedPlugin("alpha", modules("one", "two")));
        RuntimeException shared = new IllegalStateException("shared removal failure");
        jvm.failRemoval(jvm.addedTransformers().get(0), shared);
        jvm.failRemoval(jvm.addedTransformers().get(1), shared);

        assertThatThrownBy(controller::removeAll)
                .isInstanceOf(PluginInstrumentationException.class);

        assertThat(jvm.removedTransformers()).containsExactly(
                jvm.addedTransformers().get(1), jvm.addedTransformers().get(0));
        assertThat(controller.installedModuleIds("alpha")).containsExactly("one", "two");
    }

    @Test
    void laterFatalRemovalFailureWinsWhenTheFirstFailureDisablesSuppression() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        controller.install(validatedPlugin("alpha", modules("one", "two")));
        RuntimeException suppressionDisabled = new RuntimeException(null, null, false, false) { };
        jvm.failRemoval(jvm.addedTransformers().get(1), suppressionDisabled);
        jvm.failRemoval(jvm.addedTransformers().get(0), new OutOfMemoryError("fatal"));

        assertThatThrownBy(controller::removeAll)
                .isInstanceOf(OutOfMemoryError.class);

        assertThat(jvm.removedTransformers()).containsExactly(
                jvm.addedTransformers().get(1), jvm.addedTransformers().get(0));
    }

    @Test
    void findsFatalCauseBeyondTheFormerTraversalLimit() {
        Throwable failure = new OutOfMemoryError("fatal");
        for (int depth = 0; depth < 100; depth++) {
            failure = new IllegalStateException("wrapped", failure);
        }
        Throwable deeplyWrapped = failure;

        assertThatThrownBy(() -> PluginInstrumentationException.rethrowIfFatal(deeplyWrapped))
                .isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    void rejectsAnAlreadyInstalledPluginIdWithoutOverwritingHandles() {
        RecordingInstrumentation jvm = new RecordingInstrumentation();
        ByteBuddyInstrumentationController controller = controller(jvm, new ArrayList<>());
        controller.install(validatedPlugin("alpha", modules("one")));

        assertThatThrownBy(() -> controller.install(validatedPlugin("alpha", modules("two"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alpha");
        assertThat(controller.installedModuleIds("alpha")).containsExactly("one");
        assertThat(jvm.addedTransformers()).hasSize(1);
    }

    private static ByteBuddyInstrumentationController controller(
            RecordingInstrumentation instrumentation,
            List<String> reports
    ) {
        return new ByteBuddyInstrumentationController(
                instrumentation,
                AgentBuilder.InjectionStrategy.Disabled.INSTANCE,
                new TransformationErrorReporter(Clock.systemUTC(), reports::add));
    }

    private static List<InstrumentationModule> modules(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> module(id, Set.of(), List.of()))
                .toList();
    }

    private static InstrumentationModule module(
            String id,
            Set<String> helpers,
            List<TypeInstrumentation> types
    ) {
        return new InstrumentationModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<? extends TypeInstrumentation> typeInstrumentations() {
                return types;
            }

            @Override
            public Set<String> helperClassNames() {
                return helpers;
            }
        };
    }

    private static TypeInstrumentation typeInstrumentation(
            ElementMatcher<? super TypeDescription> matcher,
            AgentBuilder.Transformer transformer
    ) {
        return new TypeInstrumentation() {
            @Override
            public ElementMatcher<? super TypeDescription> typeMatcher() {
                return matcher;
            }

            @Override
            public AgentBuilder.Transformer transformer() {
                return transformer;
            }
        };
    }

    private static ValidatedPlugin validatedPlugin(
            String id,
            List<InstrumentationModule> modules
    ) {
        PluginDescriptor descriptor = new PluginDescriptor(
                id, id, "1.0.0", "1.0.0", true, 0);
        YierLoomPlugin provider = () -> descriptor;
        DiscoveredPlugin discovered = new DiscoveredPlugin(
                provider, PluginSource.BUILT_IN, Path.of("built-in", id));
        return new ValidatedPlugin(
                discovered, descriptor, PluginConfig.empty(), null, modules);
    }

    private static byte[] classBytes(Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        ClassLoader loader = type.getClassLoader();
        try (InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : loader.getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            return input.readAllBytes();
        }
    }

    private static Class<?> defineInIsolatedLoader(byte[] bytes) {
        return new ClassLoader(ByteBuddyInstrumentationControllerTest.class.getClassLoader()) {
            private Class<?> define() {
                return defineClass(null, bytes, 0, bytes.length);
            }
        }.define();
    }

    private static final class UnmodifiedTarget {
    }

    private static final class RecordingInstrumentation implements Instrumentation {
        private final List<ClassFileTransformer> addedTransformers = new ArrayList<>();
        private final List<ClassFileTransformer> removedTransformers = new ArrayList<>();
        private final Map<ClassFileTransformer, Integer> removalFailures = new IdentityHashMap<>();
        private final Map<ClassFileTransformer, Integer> falseRemovalResults = new IdentityHashMap<>();
        private final Map<ClassFileTransformer, Throwable> forcedRemovalFailures = new IdentityHashMap<>();
        private int addCalls;
        private int failingAddCall = -1;
        private RuntimeException addFailure = new IllegalStateException("installation failed");

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
            add(transformer);
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            add(transformer);
        }

        private void add(ClassFileTransformer transformer) {
            addCalls++;
            if (addCalls == failingAddCall) {
                throw addFailure;
            }
            addedTransformers.add(transformer);
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            removedTransformers.add(transformer);
            Throwable forced = forcedRemovalFailures.get(transformer);
            if (forced instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (forced instanceof Error error) {
                throw error;
            }
            int failures = removalFailures.getOrDefault(transformer, 0);
            if (failures > 0) {
                removalFailures.put(transformer, failures - 1);
                throw new IllegalStateException("removal failed");
            }
            int falseResults = falseRemovalResults.getOrDefault(transformer, 0);
            if (falseResults > 0) {
                falseRemovalResults.put(transformer, falseResults - 1);
                return false;
            }
            return true;
        }

        private byte[] transform(Class<?> type, byte[] original) throws Exception {
            byte[] current = original;
            boolean changed = false;
            String internalName = type.getName().replace('.', '/');
            ProtectionDomain domain = type.getProtectionDomain();
            for (ClassFileTransformer transformer : List.copyOf(addedTransformers)) {
                try {
                    byte[] candidate = transformer.transform(
                            type.getModule(), type.getClassLoader(), internalName, null, domain, current);
                    if (candidate != null) {
                        current = candidate;
                        changed = true;
                    }
                } catch (VirtualMachineError | ThreadDeath fatal) {
                    throw fatal;
                } catch (Throwable ignored) {
                    // The JVM treats ordinary transformer failures as no transformation.
                }
            }
            return changed ? current : null;
        }

        private List<ClassFileTransformer> addedTransformers() {
            return addedTransformers;
        }

        private List<ClassFileTransformer> removedTransformers() {
            return removedTransformers;
        }

        private void failAddCall(int call) {
            failingAddCall = call;
        }

        private void failAddCall(int call, RuntimeException failure) {
            failingAddCall = call;
            addFailure = failure;
        }

        private void failRemoval(ClassFileTransformer transformer, int times) {
            removalFailures.put(transformer, times);
        }

        private void failRemoval(ClassFileTransformer transformer, Throwable failure) {
            forcedRemovalFailures.put(transformer, failure);
        }

        private void returnFalseOnRemoval(ClassFileTransformer transformer, int times) {
            falseRemovalResults.put(transformer, times);
        }

        private void clearRemovedTransformers() {
            removedTransformers.clear();
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
                throws ClassNotFoundException, UnmodifiableClassException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isModifiableClass(Class<?> type) {
            return false;
        }

        @Override
        public Class<?>[] getAllLoadedClasses() {
            return new Class<?>[0];
        }

        @Override
        public Class<?>[] getInitiatedClasses(ClassLoader loader) {
            return new Class<?>[0];
        }

        @Override
        public long getObjectSize(Object objectToSize) {
            return 0;
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarFile) {
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarFile) {
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
        }

        @Override
        public void redefineModule(
                Module module,
                Set<Module> extraReads,
                Map<String, Set<Module>> extraExports,
                Map<String, Set<Module>> extraOpens,
                Set<Class<?>> extraUses,
                Map<Class<?>, List<Class<?>>> extraProvides
        ) {
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }
    }
}
