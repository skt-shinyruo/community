package com.nowcoder.yierloom.testkit;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.ManagedTask;
import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomApi;
import com.nowcoder.yierloom.api.YierLoomBridge;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.testkit.internal.ClassReferenceScanner;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.ConstantDynamic;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.ModuleVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.RecordComponentVisitor;
import net.bytebuddy.jar.asm.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PluginContractVerifierTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOneIsolatedCombinedPluginWithSuppressedAdviceAndClosedHelpers()
            throws Exception {
        Path jar = archives().validCombinedPlugin("valid.jar");

        PluginContractReport report = PluginContractVerifier.verify(
                jar, PluginConfig.of(Map.of("interval", "1s")));

        assertThat(report.valid()).isTrue();
        assertThat(report.violations()).isEmpty();
    }

    @Test
    void verifiesAnAlreadyLoadedProviderWithoutExternalArchiveRules() {
        RecordingProvider provider = new RecordingProvider();
        PluginConfig config = PluginConfig.of(Map.of("enabled", "true"));

        PluginContractReport report = PluginContractVerifier.verifyProvider(provider, config);

        assertThat(report.valid()).isTrue();
        assertThat(report.violations()).isEmpty();
        assertThat(provider.startConfig).isSameAs(config);
        assertThat(provider.stopCalls).hasValue(2);
        assertThat(provider.observations).hasValue(1);
        assertThat(provider.task.isCancelled()).isTrue();
        assertThatThrownBy(() -> provider.context.scheduler().scheduleWithFixedDelay(
                "late", Duration.ZERO, Duration.ofSeconds(1), () -> { }))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.context.observations().register(observation -> { }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(provider.context.events().emit(DiagnosticEvent.builder("late").build())).isFalse();
    }

    @Test
    void reportsProviderCountForMultipleDeclarations() throws Exception {
        assertCode(archives().twoProviders("two.jar"), "PROVIDER_COUNT");
    }

    @Test
    void ordinaryArchiveFailuresBecomeStableViolations() throws Exception {
        Path corrupt = temporaryDirectory.resolve("corrupt.jar");
        Files.writeString(corrupt, "not a jar");

        assertCode(temporaryDirectory.resolve("missing.jar"), "ARCHIVE_INVALID");
        assertCode(corrupt, "ARCHIVE_INVALID");
    }

    @Test
    void reportsCapabilityMissingForRootOnlyProvider() throws Exception {
        assertCode(archives().rootOnlyProvider("root-only.jar"), "CAPABILITY_MISSING");
    }

    @Test
    void externalPluginDependenciesNeverFallBackToTheParentTestClasspath() throws Exception {
        assertCode(
                archives().providerMissingDependencyFromItsOwnJar("missing-private-dependency.jar"),
                "DESCRIPTOR_INVALID");
    }

    @Test
    void parentOnlyNamespacesNeverFallBackToClassesBundledByThePlugin() throws Exception {
        assertCode(
                archives().providerBundlingFakeJdkClass("fake-jdk-class.jar"),
                "DESCRIPTOR_INVALID");
    }

    @Test
    void reportsIncompatibleApi() throws Exception {
        assertCode(archives().incompatibleApi("incompatible.jar"), "API_INCOMPATIBLE");
    }

    @Test
    void reportsDuplicateModuleIds() throws Exception {
        assertCode(archives().duplicateModules("duplicate-modules.jar"), "MODULE_ID");
    }

    @Test
    void reportsAdviceWithoutThrowableSuppression() throws Exception {
        assertCode(archives().adviceWithoutSuppression("unsuppressed.jar"),
                "ADVICE_NOT_SUPPRESSED");
    }

    @Test
    void rejectsNonInlineAdviceThatWouldRequireItsPluginClassAtRuntime() throws Exception {
        assertCode(archives().nonInlineAdvice("non-inline-advice.jar"), "ADVICE_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"method", "field", "class"})
    void reportsAdviceSelfReferencesThatWouldSurviveInlining(String kind) throws Exception {
        Path jar = switch (kind) {
            case "method" -> archives().adviceSelfMethodReference("self-method.jar");
            case "field" -> archives().adviceSelfFieldReference("self-field.jar");
            case "class" -> archives().adviceSelfClassReference("self-class.jar");
            default -> throw new IllegalArgumentException(kind);
        };

        assertCode(jar, "ADVICE_FORBIDDEN_REFERENCE");
    }

    @Test
    void reportsMissingTransitiveHelperClosure() throws Exception {
        assertCode(archives().missingTransitiveHelper("helper-closure.jar"), "HELPER_CLOSURE");
    }

    @Test
    void reportsHelperReferencesToCoreAndProviderTypes() throws Exception {
        assertCode(archives().helperReferencingCore("helper-core.jar"),
                "HELPER_FORBIDDEN_REFERENCE");
        assertCode(archives().helperReferencingProvider("helper-provider.jar"),
                "HELPER_FORBIDDEN_REFERENCE");
    }

    @Test
    void rejectsAbsentTypesThatOnlyLookLikeJdkOrPluginApiTypes() throws Exception {
        assertCode(archives().helperReferencingAbsentJavaxType("helper-absent-javax.jar"),
                "HELPER_CLOSURE");
        assertCode(archives().helperReferencingAbsentApiType("helper-absent-api.jar"),
                "HELPER_CLOSURE");
        assertCode(archives().helperReferencingWrongSourceApiType("helper-fake-api.jar"),
                "HELPER_CLOSURE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com/nowcoder/yierloom/api/Shadow.class",
            "com/nowcoder/yierloom/sdk/Shadow.class",
            "com/nowcoder/yierloom/core/Shadow.class",
            "com/nowcoder/yierloom/bootstrap/Shadow.class",
            "com/nowcoder/yierloom/plugins/Shadow.class",
            "net/bytebuddy/Shadow.class",
            "META-INF/versions/17/com/nowcoder/yierloom/api/Shadow.class"
    })
    void rejectsBundledSharedAndInternalPackages(String entry) throws Exception {
        assertCode(archives().withForbiddenEntry("forbidden-" + Math.abs(entry.hashCode()) + ".jar", entry),
                "BUNDLED_SHARED_PACKAGE");
    }

    @Test
    void acceptsValidAdviceBindingAndRejectsInvalidBindingContracts() throws Exception {
        assertThat(PluginContractVerifier.verify(archives().validBinding("binding-valid.jar")).valid())
                .isTrue();
        assertThat(PluginContractVerifier.verify(
                archives().validJdkArrayBindingValue("binding-jdk-array.jar")).valid())
                .isTrue();
        assertThat(PluginContractVerifier.verify(
                archives().validApiArrayBindingValue("binding-api-array.jar")).valid())
                .isTrue();
        assertCode(archives().invalidBindingRetention("binding-retention.jar"),
                "ADVICE_BINDING_INVALID");
        assertCode(archives().pluginClassBindingValue("binding-class.jar"),
                "ADVICE_BINDING_INVALID");
        assertCode(archives().sdkClassBindingValue("binding-sdk-class.jar"),
                "ADVICE_BINDING_INVALID");
        assertCode(archives().sdkArrayBindingValue("binding-sdk-array.jar"),
                "ADVICE_BINDING_INVALID");
        assertCode(archives().byteBuddyEnumBindingValue("binding-byte-buddy-enum.jar"),
                "ADVICE_BINDING_INVALID");
    }

    @Test
    void scansEveryJvmReferenceSurfaceUsedByAdviceAndHelpers() {
        ClassReferenceScanner.ScanResult classScan = ClassReferenceScanner.scan(referenceFixture());

        assertThat(classScan.referencedClassNames()).contains(
                "fixture.SuperDependency",
                "fixture.InterfaceDependency",
                "fixture.SignatureDependency",
                "fixture.NestHostDependency",
                "fixture.OuterDependency",
                "fixture.AnnotationDependency",
                "fixture.AnnotationValueDependency",
                "fixture.NestedAnnotationDependency",
                "fixture.PermittedDependency",
                "fixture.InnerDependency",
                "fixture.RecordDependency",
                "fixture.FieldDependency",
                "fixture.MethodArgumentDependency",
                "fixture.MethodReturnDependency",
                "fixture.ExceptionDependency",
                "fixture.FrameDependency",
                "fixture.TypeInstructionDependency",
                "fixture.FieldOwnerDependency",
                "fixture.MethodOwnerDependency",
                "fixture.IndyBootstrapDependency",
                "fixture.IndyArgumentDependency",
                "fixture.CondyBootstrapDependency",
                "fixture.NestedCondyDependency",
                "fixture.ClassLiteralDependency",
                "fixture.ArrayDependency",
                "fixture.TryCatchDependency",
                "fixture.LocalDependency",
                "fixture.LocalSignatureDependency");
        assertThat(classScan.methodBodyReferences("exercise", "()V"))
                .anySatisfy(reference -> {
                    assertThat(reference.binaryName()).isEqualTo("fixture.FieldOwnerDependency");
                    assertThat(reference.kind())
                            .isEqualTo(ClassReferenceScanner.ReferenceKind.FIELD_OWNER);
                })
                .anySatisfy(reference -> {
                    assertThat(reference.binaryName()).isEqualTo("fixture.MethodOwnerDependency");
                    assertThat(reference.kind())
                            .isEqualTo(ClassReferenceScanner.ReferenceKind.METHOD_OWNER);
                })
                .anySatisfy(reference -> {
                    assertThat(reference.binaryName()).isEqualTo("fixture.IndyBootstrapDependency");
                    assertThat(reference.kind())
                            .isEqualTo(ClassReferenceScanner.ReferenceKind.HANDLE_OWNER);
                })
                .anySatisfy(reference -> {
                    assertThat(reference.binaryName()).isEqualTo("fixture.ClassLiteralDependency");
                    assertThat(reference.kind())
                            .isEqualTo(ClassReferenceScanner.ReferenceKind.CLASS_LITERAL);
                });

        ClassReferenceScanner.ScanResult moduleScan = ClassReferenceScanner.scan(moduleFixture());
        assertThat(moduleScan.referencedClassNames()).containsExactlyInAnyOrder(
                "fixture.ModuleMainDependency",
                "fixture.ModuleServiceDependency",
                "fixture.ModuleProviderDependency");
    }

    @Test
    void customTransformerIsInformationalAndDoesNotInvalidateReport() throws Exception {
        PluginContractReport report = PluginContractVerifier.verify(
                archives().customTransformer("custom.jar"));

        assertThat(report.valid()).isTrue();
        assertThat(report.violations())
                .extracting(PluginViolation::code)
                .containsExactly("CUSTOM_TRANSFORMER_UNPROVEN");
        assertThat(report.violations().get(0).severity()).isEqualTo(PluginViolationSeverity.INFO);
    }

    @Test
    void startFailureStillStopsTwiceAndReleasesBridge() {
        StartFailureProvider provider = new StartFailureProvider();

        PluginContractReport report = PluginContractVerifier.verifyProvider(
                provider, PluginConfig.empty());

        assertThat(report.valid()).isFalse();
        assertThat(report.violations()).extracting(PluginViolation::code)
                .contains("LIFECYCLE_CONTRACT");
        assertThat(report.violations()).extracting(PluginViolation::detail)
                .noneMatch(detail -> detail.contains("private start detail"));
        assertThat(provider.stopCalls).hasValue(2);
        assertBridgeAvailable();
    }

    @Test
    void firstStopFailureDoesNotPreventSecondStopOrCleanup() {
        FirstStopFailureProvider provider = new FirstStopFailureProvider();

        PluginContractReport report = PluginContractVerifier.verifyProvider(
                provider, PluginConfig.empty());

        assertThat(report.valid()).isFalse();
        assertThat(provider.stopCalls).hasValue(2);
        assertThat(report.violations())
                .anySatisfy(violation -> {
                    assertThat(violation.code()).isEqualTo("LIFECYCLE_CONTRACT");
                    assertThat(violation.location()).isEqualTo("stop[1]");
                });
        assertBridgeAvailable();
    }

    @Test
    void nonIdempotentSecondStopIsReported() {
        SecondStopFailureProvider provider = new SecondStopFailureProvider();

        PluginContractReport report = PluginContractVerifier.verifyProvider(
                provider, PluginConfig.empty());

        assertThat(report.valid()).isFalse();
        assertThat(provider.stopCalls).hasValue(2);
        assertThat(report.violations()).extracting(PluginViolation::location)
                .contains("stop[2]");
    }

    @Test
    void foreignObservationIsRejectedWithoutReachingHandler() {
        ForeignProbeProvider provider = new ForeignProbeProvider();

        PluginContractReport report = PluginContractVerifier.verifyProvider(
                provider, PluginConfig.empty());

        assertThat(report.valid()).isTrue();
        assertThat(provider.foreignAccepted).isFalse();
        assertThat(provider.observations).hasValue(1);
    }

    @Test
    void existingBridgeEndpointIsNeitherReplacedNorCleared() {
        SentinelEndpoint sentinel = new SentinelEndpoint();
        RecordingProvider provider = new RecordingProvider();
        assertThat(YierLoomBridge.install(sentinel)).isTrue();
        try {
            PluginContractReport report = PluginContractVerifier.verifyProvider(
                    provider, PluginConfig.empty());

            assertThat(report.valid()).isFalse();
            assertThat(provider.startCalls).hasValue(0);
            assertThat(YierLoomBridge.emit(
                    "sentinel", DiagnosticEvent.builder("still-installed").build())).isTrue();
            assertThat(sentinel.events).hasValue(1);
        } finally {
            assertThat(YierLoomBridge.clear(sentinel)).isTrue();
        }
    }

    @Test
    void wrappedVmFatalEscapesAfterLifecycleCleanup() {
        TestVmError fatal = new TestVmError();
        FatalStartProvider provider = new FatalStartProvider(fatal);

        assertThatThrownBy(() -> PluginContractVerifier.verifyProvider(
                provider, PluginConfig.empty()))
                .isSameAs(fatal);

        assertThat(provider.stopCalls).hasValue(2);
        assertBridgeAvailable();
    }

    @Test
    void fatalSearchIsUnboundedAndIdentityCycleSafe() {
        TestVmError fatal = new TestVmError();
        CyclicFailure first = new CyclicFailure();
        CyclicFailure current = first;
        for (int index = 0; index < 512; index++) {
            CyclicFailure next = new CyclicFailure();
            current.next = next;
            current = next;
        }
        current.next = first;
        current.addSuppressed(fatal);
        DescriptorFailureProvider provider = new DescriptorFailureProvider(first);

        assertThatThrownBy(() -> PluginContractVerifier.verifyProvider(
                provider, PluginConfig.empty()))
                .isSameAs(fatal);
    }

    @Test
    void fatalSearchSurvivesThrowingCauseAccessors() {
        TestVmError fatal = new TestVmError();
        CyclicFailure accessorFailure = new CyclicFailure();
        ThrowingCauseFailure descriptorFailure = new ThrowingCauseFailure(accessorFailure);
        accessorFailure.next = descriptorFailure;
        accessorFailure.addSuppressed(fatal);

        assertThatThrownBy(() -> PluginContractVerifier.verifyProvider(
                new DescriptorFailureProvider(descriptorFailure), PluginConfig.empty()))
                .isSameAs(fatal);
    }

    @Test
    void verifyOrThrowCarriesStableViolations() throws Exception {
        Path invalid = archives().rootOnlyProvider("invalid-for-throw.jar");

        assertThatExceptionOfType(PluginContractException.class)
                .isThrownBy(() -> PluginContractVerifier.verifyOrThrow(invalid))
                .satisfies(failure -> {
                    assertThat(failure.violations()).extracting(PluginViolation::code)
                            .contains("CAPABILITY_MISSING");
                    assertThat(failure.getMessage()).doesNotContain(invalid.toString());
                });
    }

    private TestPluginArchiveBuilder archives() {
        return new TestPluginArchiveBuilder(temporaryDirectory);
    }

    private void assertCode(Path jar, String expectedCode) {
        assertThat(PluginContractVerifier.verify(jar).violations())
                .extracting(PluginViolation::code)
                .contains(expectedCode);
    }

    private static void assertBridgeAvailable() {
        SentinelEndpoint sentinel = new SentinelEndpoint();
        assertThat(YierLoomBridge.install(sentinel)).isTrue();
        assertThat(YierLoomBridge.clear(sentinel)).isTrue();
    }

    private static byte[] referenceFixture() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_RECORD,
                "fixture/ReferenceFixture",
                "Ljava/lang/Record;Ljava/util/function/Supplier<Lfixture/SignatureDependency;>;",
                "fixture/SuperDependency",
                new String[]{"fixture/InterfaceDependency"});
        writer.visitNestHost("fixture/NestHostDependency");
        writer.visitOuterClass(
                "fixture/OuterDependency",
                "outer",
                "(Lfixture/MethodArgumentDependency;)Lfixture/MethodReturnDependency;");
        writer.visitPermittedSubclass("fixture/PermittedDependency");
        writer.visitInnerClass(
                "fixture/InnerDependency", "fixture/OuterDependency", "Inner", 0);
        AnnotationVisitor annotation = writer.visitAnnotation(
                "Lfixture/AnnotationDependency;", true);
        annotation.visit("type", Type.getType("Lfixture/AnnotationValueDependency;"));
        annotation.visitEnum("choice", "Lfixture/EnumDependency;", "VALUE");
        AnnotationVisitor nested = annotation.visitAnnotation(
                "nested", "Lfixture/NestedAnnotationDependency;");
        nested.visitEnd();
        annotation.visitEnd();

        RecordComponentVisitor component = writer.visitRecordComponent(
                "recordValue",
                "Lfixture/RecordDependency;",
                "Ljava/util/List<Lfixture/RecordSignatureDependency;>;");
        component.visitAnnotation("Lfixture/RecordAnnotationDependency;", true).visitEnd();
        component.visitEnd();
        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "field",
                "Lfixture/FieldDependency;",
                "Ljava/util/List<Lfixture/FieldSignatureDependency;>;",
                null).visitEnd();

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "exercise",
                "()V",
                "()V^Lfixture/GenericExceptionDependency;",
                new String[]{"fixture/ExceptionDependency"});
        method.visitCode();
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        method.visitLabel(start);
        method.visitFrame(
                Opcodes.F_FULL,
                1,
                new Object[]{"fixture/FrameDependency"},
                0,
                new Object[0]);
        method.visitTypeInsn(Opcodes.CHECKCAST, "fixture/TypeInstructionDependency");
        method.visitFieldInsn(
                Opcodes.GETSTATIC,
                "fixture/FieldOwnerDependency",
                "value",
                "Lfixture/FieldValueDependency;");
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "fixture/MethodOwnerDependency",
                "call",
                "(Lfixture/MethodArgumentDependency;)Lfixture/MethodReturnDependency;",
                false);
        Handle condyBootstrap = new Handle(
                Opcodes.H_INVOKESTATIC,
                "fixture/CondyBootstrapDependency",
                "bootstrap",
                "()Ljava/lang/Object;",
                false);
        ConstantDynamic nestedCondy = new ConstantDynamic(
                "nested",
                "Lfixture/CondyValueDependency;",
                condyBootstrap,
                Type.getType("Lfixture/NestedCondyDependency;"));
        Handle indyBootstrap = new Handle(
                Opcodes.H_INVOKESTATIC,
                "fixture/IndyBootstrapDependency",
                "bootstrap",
                "()Ljava/lang/invoke/CallSite;",
                false);
        method.visitInvokeDynamicInsn(
                "dynamic",
                "()V",
                indyBootstrap,
                Type.getType("Lfixture/IndyArgumentDependency;"),
                nestedCondy);
        method.visitLdcInsn(Type.getType("Lfixture/ClassLiteralDependency;"));
        method.visitLdcInsn(new ConstantDynamic(
                "constant",
                "Lfixture/CondyResultDependency;",
                condyBootstrap,
                nestedCondy));
        method.visitMultiANewArrayInsn("[[Lfixture/ArrayDependency;", 2);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(end);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.ATHROW);
        method.visitTryCatchBlock(start, end, handler, "fixture/TryCatchDependency");
        method.visitLocalVariable(
                "local",
                "Lfixture/LocalDependency;",
                "Ljava/util/List<Lfixture/LocalSignatureDependency;>;",
                start,
                end,
                0);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] moduleFixture() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor module = writer.visitModule("fixture.module", 0, null);
        module.visitMainClass("fixture/ModuleMainDependency");
        module.visitUse("fixture/ModuleServiceDependency");
        module.visitProvide(
                "fixture/ModuleServiceDependency", "fixture/ModuleProviderDependency");
        module.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private abstract static class RuntimeProvider implements YierLoomPlugin, RuntimeCapability {
        final AtomicInteger stopCalls = new AtomicInteger();

        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor(
                    "built-in-sample", "Built-in sample", "1.0.0",
                    YierLoomApi.VERSION, true, 0);
        }
    }

    private static final class RecordingProvider extends RuntimeProvider {
        private final AtomicInteger startCalls = new AtomicInteger();
        private final AtomicInteger observations = new AtomicInteger();
        private PluginConfig startConfig;
        private PluginRuntimeContext context;
        private ManagedTask task;

        @Override
        public void start(PluginRuntimeContext context) {
            startCalls.incrementAndGet();
            this.context = context;
            startConfig = context.config();
            context.observations().register(observation -> observations.incrementAndGet());
            task = context.scheduler().scheduleWithFixedDelay(
                    "record", Duration.ZERO, Duration.ofSeconds(1), () -> { });
        }

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
        }
    }

    private static final class StartFailureProvider extends RuntimeProvider {
        @Override
        public void start(PluginRuntimeContext context) {
            throw new IllegalStateException("private start detail");
        }

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
        }
    }

    private static final class FirstStopFailureProvider extends RuntimeProvider {
        @Override
        public void start(PluginRuntimeContext context) {
        }

        @Override
        public void stop() {
            if (stopCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("private first-stop detail");
            }
        }
    }

    private static final class SecondStopFailureProvider extends RuntimeProvider {
        @Override
        public void start(PluginRuntimeContext context) {
        }

        @Override
        public void stop() {
            if (stopCalls.incrementAndGet() == 2) {
                throw new IllegalStateException("not idempotent");
            }
        }
    }

    private static final class ForeignProbeProvider extends RuntimeProvider {
        private final AtomicInteger observations = new AtomicInteger();
        private boolean foreignAccepted;

        @Override
        public void start(PluginRuntimeContext context) {
            context.observations().register(observation -> observations.incrementAndGet());
            foreignAccepted = YierLoomBridge.observe(
                    "another-plugin", PluginObservation.builder("foreign").build());
        }

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
        }
    }

    private static final class FatalStartProvider extends RuntimeProvider {
        private final TestVmError fatal;

        private FatalStartProvider(TestVmError fatal) {
            this.fatal = fatal;
        }

        @Override
        public void start(PluginRuntimeContext context) {
            throw new IllegalStateException("wrapped", fatal);
        }

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
        }
    }

    private static final class DescriptorFailureProvider implements YierLoomPlugin {
        private final RuntimeException failure;

        private DescriptorFailureProvider(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public PluginDescriptor descriptor() {
            throw failure;
        }
    }

    private static final class SentinelEndpoint implements YierLoomBridge.Endpoint {
        private final AtomicInteger events = new AtomicInteger();

        @Override
        public boolean observe(String pluginId, PluginObservation observation) {
            return "sentinel".equals(pluginId);
        }

        @Override
        public boolean emit(String pluginId, DiagnosticEvent event) {
            if (!"sentinel".equals(pluginId)) {
                return false;
            }
            events.incrementAndGet();
            return true;
        }
    }

    private static final class TestVmError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }

    private static final class CyclicFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private Throwable next;

        @Override
        public synchronized Throwable getCause() {
            return next;
        }
    }

    private static final class ThrowingCauseFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final RuntimeException accessorFailure;

        private ThrowingCauseFailure(RuntimeException accessorFailure) {
            this.accessorFailure = accessorFailure;
        }

        @Override
        public synchronized Throwable getCause() {
            throw accessorFailure;
        }
    }

    public static final class ParentOnlyDependency {
        private ParentOnlyDependency() {
        }

        public static String value() {
            return "Parent-only dependency";
        }
    }
}
