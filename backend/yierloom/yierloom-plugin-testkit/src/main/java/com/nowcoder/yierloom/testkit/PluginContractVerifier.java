package com.nowcoder.yierloom.testkit;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomApi;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.sdk.AdviceBinding;
import com.nowcoder.yierloom.sdk.AdviceTransformer;
import com.nowcoder.yierloom.sdk.InstrumentationCapability;
import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import com.nowcoder.yierloom.testkit.internal.ClassReferenceScanner;
import com.nowcoder.yierloom.testkit.internal.ClassReferenceScanner.Reference;
import com.nowcoder.yierloom.testkit.internal.ContractPluginClassLoader;
import com.nowcoder.yierloom.testkit.internal.ContractRuntimeHarness;
import com.nowcoder.yierloom.testkit.internal.PluginArchive;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.jar.asm.Type;

public final class PluginContractVerifier {
    private static final Pattern MODULE_ID = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final List<String> JDK_PREFIXES = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "org.ietf.jgss.", "org.w3c.dom.", "org.xml.sax.");
    private static final List<String> HELPER_FORBIDDEN_PREFIXES = List.of(
            "com.nowcoder.yierloom.sdk.",
            "com.nowcoder.yierloom.core.",
            "com.nowcoder.yierloom.bootstrap.",
            "com.nowcoder.yierloom.plugins.",
            "com.nowcoder.yierloom.testkit.",
            "net.bytebuddy.");

    private PluginContractVerifier() {
    }

    public static PluginContractReport verify(Path pluginJar) {
        return verify(pluginJar, PluginConfig.empty());
    }

    public static PluginContractReport verify(Path pluginJar, PluginConfig config) {
        Objects.requireNonNull(config, "config");
        Collector violations = new Collector();
        FatalAccumulator fatal = new FatalAccumulator();
        PluginArchive archive = null;
        ContractPluginClassLoader loader = null;
        try {
            archive = PluginArchive.open(pluginJar);
            List<String> forbiddenEntries = archive.forbiddenClassEntries();
            for (String entry : forbiddenEntries) {
                violations.error(
                        "BUNDLED_SHARED_PACKAGE", entry, "Shared or internal class is bundled");
            }

            List<String> declarations = archive.providerDeclarations();
            if (declarations.size() != 1 || !isBinaryName(firstOrNull(declarations))) {
                violations.error(
                        "PROVIDER_COUNT", "META-INF/services", "Expected exactly one provider");
            } else if (forbiddenEntries.isEmpty()) {
                String providerName = declarations.get(0);
                loader = new ContractPluginClassLoader(
                        archive.path(), PluginContractVerifier.class.getClassLoader());
                YierLoomPlugin provider = loadProvider(providerName, loader, violations);
                if (provider != null) {
                    verifyProvider(
                            provider,
                            config,
                            archive::classBytes,
                            loader,
                            violations);
                }
            }
        } catch (Throwable failure) {
            if (!fatal.record(failure)) {
                violations.error(
                        "ARCHIVE_INVALID", "archive", failure.getClass().getName());
            }
        } finally {
            close(loader, "classloader", violations, fatal);
            close(archive, "archive", violations, fatal);
        }
        fatal.rethrow();
        return violations.report();
    }

    public static PluginContractReport verifyOrThrow(Path pluginJar) {
        PluginContractReport report = verify(pluginJar);
        report.throwIfInvalid();
        return report;
    }

    public static PluginContractReport verifyOrThrow(Path pluginJar, PluginConfig config) {
        PluginContractReport report = verify(pluginJar, config);
        report.throwIfInvalid();
        return report;
    }

    public static PluginContractReport verifyProvider(
            YierLoomPlugin provider,
            PluginConfig config
    ) {
        Objects.requireNonNull(config, "config");
        Collector violations = new Collector();
        if (provider == null) {
            violations.error("PROVIDER_TYPE", "provider", "Provider is null");
            return violations.report();
        }
        ClassLoader owner = provider.getClass().getClassLoader();
        FatalAccumulator fatal = new FatalAccumulator();
        try {
            verifyProvider(
                    provider,
                    config,
                    binaryName -> classBytes(owner, binaryName),
                    owner,
                    violations);
        } catch (Throwable failure) {
            if (!fatal.record(failure)) {
                violations.error("VERIFIER_FAILURE", "provider", failure.getClass().getName());
            }
        }
        fatal.rethrow();
        return violations.report();
    }

    public static PluginContractReport verifyProvider(YierLoomPlugin provider) {
        return verifyProvider(provider, PluginConfig.empty());
    }

    public static PluginContractReport verifyOrThrow(
            YierLoomPlugin provider,
            PluginConfig config
    ) {
        PluginContractReport report = verifyProvider(provider, config);
        report.throwIfInvalid();
        return report;
    }

    private static YierLoomPlugin loadProvider(
            String providerName,
            ContractPluginClassLoader loader,
            Collector violations
    ) {
        Class<?> providerType;
        try {
            providerType = Class.forName(providerName, true, loader);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error("PROVIDER_LOAD", providerName, failure.getClass().getName());
            return null;
        }
        if (providerType.getClassLoader() != loader
                || !YierLoomPlugin.class.isAssignableFrom(providerType)) {
            violations.error("PROVIDER_TYPE", providerName, "Provider type is not isolated");
            return null;
        }
        if (!Modifier.isPublic(providerType.getModifiers())
                || providerType.isInterface()
                || Modifier.isAbstract(providerType.getModifiers())) {
            violations.error("PROVIDER_CONSTRUCTOR", providerName, "Provider type is not concrete");
            return null;
        }
        Constructor<?> constructor;
        try {
            constructor = providerType.getConstructor();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error(
                    "PROVIDER_CONSTRUCTOR", providerName, failure.getClass().getName());
            return null;
        }
        try {
            return (YierLoomPlugin) constructor.newInstance();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error(
                    "PROVIDER_INSTANTIATION", providerName, failure.getClass().getName());
            return null;
        }
    }

    private static void verifyProvider(
            YierLoomPlugin provider,
            PluginConfig config,
            ClassBytes classBytes,
            ClassLoader owner,
            Collector violations
    ) {
        PluginDescriptor descriptor;
        try {
            descriptor = provider.descriptor();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error(
                    "DESCRIPTOR_INVALID", "descriptor", failure.getClass().getName());
            return;
        }
        if (descriptor == null) {
            violations.error("DESCRIPTOR_INVALID", "descriptor", "Descriptor is null");
            return;
        }
        if (!apiCompatible(YierLoomApi.VERSION, descriptor.apiVersion())) {
            violations.error(
                    "API_INCOMPATIBLE", descriptor.id(), "Plugin API version is incompatible");
            return;
        }

        RuntimeCapability runtime = provider instanceof RuntimeCapability candidate ? candidate : null;
        InstrumentationCapability instrumentation =
                provider instanceof InstrumentationCapability candidate ? candidate : null;
        if (runtime == null && instrumentation == null) {
            violations.error(
                    "CAPABILITY_MISSING", descriptor.id(), "Plugin declares no capability");
            return;
        }

        List<ModulePlan> modules = instrumentation == null
                ? List.of()
                : readModules(instrumentation, config, violations);
        if (instrumentation != null && runtime == null && modules.isEmpty()) {
            violations.error(
                    "CAPABILITY_MISSING", descriptor.id(),
                    "Instrumentation-only plugin declares no module");
        }

        validateAdviceAndHelpers(
                provider,
                owner,
                modules,
                classBytes,
                violations);

        if (runtime != null) {
            try {
                violations.addAll(ContractRuntimeHarness.verify(
                        descriptor.id(), runtime, config));
            } catch (Throwable failure) {
                rethrowFatal(failure);
                violations.error(
                        "LIFECYCLE_CONTRACT", "runtime", failure.getClass().getName());
            }
        }
    }

    private static List<ModulePlan> readModules(
            InstrumentationCapability instrumentation,
            PluginConfig config,
            Collector violations
    ) {
        List<InstrumentationModule> declarations;
        try {
            declarations = instrumentation.instrumentations(config);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error(
                    "INSTRUMENTATION_INVALID", "instrumentations", failure.getClass().getName());
            return List.of();
        }
        if (declarations == null) {
            violations.error(
                    "INSTRUMENTATION_INVALID", "instrumentations", "Module list is null");
            return List.of();
        }

        List<ModulePlan> modules = new ArrayList<>();
        Set<String> moduleIds = new HashSet<>();
        Map<String, String> helperOwners = new HashMap<>();
        for (int index = 0; index < declarations.size(); index++) {
            InstrumentationModule module = declarations.get(index);
            String fallbackLocation = "module[" + index + "]";
            if (module == null) {
                violations.error(
                        "INSTRUMENTATION_INVALID", fallbackLocation, "Module is null");
                continue;
            }
            String moduleId;
            try {
                moduleId = module.id();
            } catch (Throwable failure) {
                rethrowFatal(failure);
                violations.error("MODULE_ID", fallbackLocation, failure.getClass().getName());
                continue;
            }
            String location = moduleId == null ? fallbackLocation : moduleId;
            if (moduleId == null
                    || !MODULE_ID.matcher(moduleId).matches()
                    || !moduleIds.add(moduleId)) {
                violations.error("MODULE_ID", location, "Module ID is invalid or duplicated");
            }

            Set<String> helpers = readHelpers(
                    module, location, fallbackLocation, helperOwners, violations);
            List<TypePlan> types = readTypes(module, location, violations);
            modules.add(new ModulePlan(location, helpers, types));
        }
        return List.copyOf(modules);
    }

    private static Set<String> readHelpers(
            InstrumentationModule module,
            String moduleId,
            String ownerToken,
            Map<String, String> helperOwners,
            Collector violations
    ) {
        Set<String> declared;
        try {
            declared = module.helperClassNames();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error("HELPER_NAME", moduleId, failure.getClass().getName());
            return Set.of();
        }
        if (declared == null) {
            violations.error("HELPER_NAME", moduleId, "Helper set is null");
            return Set.of();
        }
        List<String> sorted = new ArrayList<>();
        for (String helper : declared) {
            if (!isBinaryName(helper)) {
                violations.error("HELPER_NAME", moduleId, "Helper binary name is invalid");
            } else {
                sorted.add(helper);
            }
        }
        sorted.sort(Comparator.naturalOrder());
        LinkedHashSet<String> helpers = new LinkedHashSet<>();
        for (String helper : sorted) {
            helpers.add(helper);
            String previous = helperOwners.putIfAbsent(helper, ownerToken);
            if (previous != null && !previous.equals(ownerToken)) {
                violations.error(
                        "HELPER_DUPLICATE_OWNER", helper,
                        "Helper is declared by more than one module");
            }
        }
        return Collections.unmodifiableSet(helpers);
    }

    private static List<TypePlan> readTypes(
            InstrumentationModule module,
            String moduleId,
            Collector violations
    ) {
        List<? extends TypeInstrumentation> declared;
        try {
            declared = module.typeInstrumentations();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error(
                    "INSTRUMENTATION_INVALID", moduleId, failure.getClass().getName());
            return List.of();
        }
        if (declared == null) {
            violations.error(
                    "INSTRUMENTATION_INVALID", moduleId, "Type instrumentation list is null");
            return List.of();
        }
        List<TypePlan> types = new ArrayList<>();
        for (int index = 0; index < declared.size(); index++) {
            TypeInstrumentation type = declared.get(index);
            String location = moduleId + "/type[" + index + "]";
            if (type == null) {
                violations.error(
                        "INSTRUMENTATION_INVALID", location, "Type instrumentation is null");
                continue;
            }
            AgentBuilder.Transformer transformer;
            try {
                Objects.requireNonNull(type.typeMatcher(), "type matcher");
                Objects.requireNonNull(type.classLoaderMatcher(), "ClassLoader matcher");
                transformer = Objects.requireNonNull(type.transformer(), "transformer");
            } catch (Throwable failure) {
                rethrowFatal(failure);
                violations.error(
                        "INSTRUMENTATION_INVALID", location, failure.getClass().getName());
                continue;
            }
            types.add(new TypePlan(location, transformer));
        }
        return List.copyOf(types);
    }

    private static void validateAdviceAndHelpers(
            YierLoomPlugin provider,
            ClassLoader owner,
            List<ModulePlan> modules,
            ClassBytes classBytes,
            Collector violations
    ) {
        Set<String> adviceClassNames = new LinkedHashSet<>();
        for (ModulePlan module : modules) {
            for (TypePlan type : module.types()) {
                if (type.transformer() instanceof AdviceTransformer advice) {
                    Class<?> adviceClass = adviceClass(advice, type.location(), violations);
                    if (adviceClass == null) {
                        continue;
                    }
                    adviceClassNames.add(adviceClass.getName());
                    validateAdvice(
                            advice,
                            adviceClass,
                            owner,
                            module.helpers(),
                            type.location(),
                            classBytes,
                            violations);
                } else {
                    violations.info(
                            "CUSTOM_TRANSFORMER_UNPROVEN",
                            type.location(),
                            "Custom transformer cannot be proven fail-open");
                }
            }
        }

        Set<String> forbiddenTypes = new LinkedHashSet<>(adviceClassNames);
        forbiddenTypes.add(provider.getClass().getName());
        for (ModulePlan module : modules) {
            validateHelpers(
                    module,
                    forbiddenTypes,
                    classBytes,
                    violations);
        }
    }

    private static Class<?> adviceClass(
            AdviceTransformer advice,
            String location,
            Collector violations
    ) {
        try {
            return Objects.requireNonNull(advice.adviceClass(), "Advice class");
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error("ADVICE_INVALID", location, failure.getClass().getName());
            return null;
        }
    }

    private static void validateAdvice(
            AdviceTransformer advice,
            Class<?> adviceClass,
            ClassLoader owner,
            Set<String> helpers,
            String location,
            ClassBytes classBytes,
            Collector violations
    ) {
        if (adviceClass.getClassLoader() != owner) {
            violations.error(
                    "ADVICE_INVALID", location, "Advice class is not owned by the plugin");
            return;
        }
        ClassReferenceScanner.ScanResult scan;
        try {
            byte[] bytes = classBytes.read(adviceClass.getName());
            if (bytes == null) {
                violations.error("ADVICE_INVALID", adviceClass.getName(), "Advice class is missing");
                return;
            }
            scan = ClassReferenceScanner.scan(bytes);
            if (!adviceClass.getName().equals(scan.className())) {
                violations.error(
                        "ADVICE_INVALID", adviceClass.getName(), "Advice class name mismatches");
                return;
            }
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error(
                    "ADVICE_INVALID", adviceClass.getName(), failure.getClass().getName());
            return;
        }

        List<Method> adviceMethods = adviceMethods(adviceClass, violations);
        if (adviceMethods.isEmpty()) {
            violations.error(
                    "ADVICE_INVALID", adviceClass.getName(), "No Advice method is declared");
        }
        for (Method method : adviceMethods) {
            Advice.OnMethodEnter enter = method.getDeclaredAnnotation(Advice.OnMethodEnter.class);
            Advice.OnMethodExit exit = method.getDeclaredAnnotation(Advice.OnMethodExit.class);
            if (!Modifier.isStatic(method.getModifiers())
                    || (enter != null && exit != null)
                    || (enter != null && !enter.inline())
                    || (exit != null && !exit.inline())) {
                violations.error(
                        "ADVICE_INVALID",
                        methodLocation(adviceClass, method),
                        "Advice method must be one static inline entry or exit");
            }
            if ((enter != null && enter.suppress() != Throwable.class)
                    || (exit != null && exit.suppress() != Throwable.class)) {
                violations.error(
                        "ADVICE_NOT_SUPPRESSED",
                        methodLocation(adviceClass, method),
                        "Advice must suppress Throwable");
            }
            validateAdviceBody(adviceClass, method, helpers, scan, violations);
        }
        validateBindings(advice, adviceClass, adviceMethods, location, violations);
    }

    private static List<Method> adviceMethods(Class<?> adviceClass, Collector violations) {
        try {
            return Arrays.stream(adviceClass.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Advice.OnMethodEnter.class)
                            || method.isAnnotationPresent(Advice.OnMethodExit.class))
                    .sorted(Comparator
                            .comparing(Method::getName)
                            .thenComparing(method -> Type.getMethodDescriptor(method)))
                    .toList();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error(
                    "ADVICE_INVALID", adviceClass.getName(), failure.getClass().getName());
            return List.of();
        }
    }

    private static void validateAdviceBody(
            Class<?> adviceClass,
            Method method,
            Set<String> helpers,
            ClassReferenceScanner.ScanResult scan,
            Collector violations
    ) {
        String descriptor = Type.getMethodDescriptor(method);
        for (Reference reference : scan.methodBodyReferences(method.getName(), descriptor)) {
            String name = reference.binaryName();
            if (isJdk(name) || isApi(name) || helpers.contains(name)) {
                continue;
            }
            violations.error(
                    "ADVICE_FORBIDDEN_REFERENCE",
                    methodLocation(adviceClass, method),
                    name + " (" + reference.kind() + ")");
        }
    }

    private static void validateBindings(
            AdviceTransformer advice,
            Class<?> adviceClass,
            List<Method> adviceMethods,
            String location,
            Collector violations
    ) {
        List<AdviceBinding> bindings;
        try {
            bindings = advice.bindings();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            violations.error("ADVICE_BINDING_INVALID", location, failure.getClass().getName());
            return;
        }
        if (bindings == null) {
            violations.error("ADVICE_BINDING_INVALID", location, "Binding list is null");
            return;
        }
        for (int index = 0; index < bindings.size(); index++) {
            AdviceBinding binding = bindings.get(index);
            String bindingLocation = adviceClass.getName() + "/binding[" + index + "]";
            if (binding == null) {
                violations.error(
                        "ADVICE_BINDING_INVALID", bindingLocation, "Binding is null");
                continue;
            }
            Class<? extends Annotation> annotationType = binding.annotationType();
            Retention retention = annotationType.getAnnotation(Retention.class);
            Target target = annotationType.getAnnotation(Target.class);
            boolean runtimeRetention = retention != null
                    && retention.value() == RetentionPolicy.RUNTIME;
            boolean parameterTarget = target != null
                    && Arrays.asList(target.value()).contains(ElementType.PARAMETER);
            boolean used = adviceMethods.stream()
                    .flatMap(method -> Arrays.stream(method.getParameterAnnotations()))
                    .flatMap(Arrays::stream)
                    .anyMatch(annotation -> annotation.annotationType() == annotationType);
            if (!runtimeRetention || !parameterTarget || !used) {
                violations.error(
                        "ADVICE_BINDING_INVALID", bindingLocation,
                        "Binding annotation contract is invalid");
            }
            Object value = binding.value();
            Class<?> valueType = null;
            if (value instanceof Class<?> type) {
                valueType = type;
            } else if (value instanceof Enum<?> enumValue) {
                valueType = enumValue.getDeclaringClass();
            }
            if (valueType != null && !isTargetVisible(valueType)) {
                violations.error(
                        "ADVICE_BINDING_INVALID", bindingLocation,
                        "Binding constant type is not target-visible");
            }
        }
    }

    private static void validateHelpers(
            ModulePlan module,
            Set<String> forbiddenTypes,
            ClassBytes classBytes,
            Collector violations
    ) {
        for (String helper : module.helpers()) {
            ClassReferenceScanner.ScanResult scan;
            try {
                byte[] bytes = classBytes.read(helper);
                if (bytes == null) {
                    violations.error("HELPER_MISSING", helper, "Helper class is missing");
                    continue;
                }
                scan = ClassReferenceScanner.scan(bytes);
                if (!helper.equals(scan.className())) {
                    violations.error("HELPER_MISSING", helper, "Helper class name mismatches");
                    continue;
                }
            } catch (Throwable failure) {
                rethrowFatal(failure);
                violations.error("HELPER_MISSING", helper, failure.getClass().getName());
                continue;
            }

            for (String reference : scan.referencedClassNames()) {
                if (reference.equals(helper)
                        || isJdk(reference)
                        || isApi(reference)
                        || module.helpers().contains(reference)) {
                    continue;
                }
                if (forbiddenTypes.contains(reference)
                        || HELPER_FORBIDDEN_PREFIXES.stream().anyMatch(reference::startsWith)) {
                    violations.error(
                            "HELPER_FORBIDDEN_REFERENCE", helper, reference);
                } else {
                    violations.error("HELPER_CLOSURE", helper, reference);
                }
            }
        }
    }

    private static boolean apiCompatible(String current, String required) {
        Version currentVersion = version(current);
        Version requiredVersion = version(required);
        return currentVersion != null
                && requiredVersion != null
                && currentVersion.major() == requiredVersion.major()
                && currentVersion.minor() >= requiredVersion.minor();
    }

    private static Version version(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = VERSION.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new Version(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    private static boolean isBinaryName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        for (String part : parts) {
            if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0))) {
                return false;
            }
            for (int index = 1; index < part.length(); index++) {
                if (!Character.isJavaIdentifierPart(part.charAt(index))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isJdk(String binaryName) {
        if (!hasPrefix(binaryName, JDK_PREFIXES)) {
            return false;
        }
        Class<?> type = resolve(binaryName, ClassLoader.getPlatformClassLoader());
        return type != null && hasJdkOrigin(type);
    }

    private static boolean isApi(String binaryName) {
        if (!binaryName.startsWith("com.nowcoder.yierloom.api.")) {
            return false;
        }
        Class<?> type = resolve(binaryName, YierLoomApi.class.getClassLoader());
        return type != null && hasApiOrigin(type);
    }

    private static boolean isTargetVisible(Class<?> declaredType) {
        Class<?> type = componentType(declaredType);
        if (type.isPrimitive()) {
            return true;
        }
        if (hasPrefix(type.getName(), JDK_PREFIXES)) {
            Class<?> resolved = resolve(type.getName(), ClassLoader.getPlatformClassLoader());
            return resolved == type && hasJdkOrigin(type);
        }
        if (type.getName().startsWith("com.nowcoder.yierloom.api.")) {
            Class<?> resolved = resolve(type.getName(), YierLoomApi.class.getClassLoader());
            return resolved == type && hasApiOrigin(type);
        }
        return false;
    }

    private static Class<?> componentType(Class<?> type) {
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        return component;
    }

    private static Class<?> resolve(String binaryName, ClassLoader loader) {
        try {
            return Class.forName(binaryName, false, loader);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return null;
        }
    }

    private static boolean hasJdkOrigin(Class<?> type) {
        try {
            Module module = type.getModule();
            ClassLoader loader = type.getClassLoader();
            return (loader == null || loader == ClassLoader.getPlatformClassLoader())
                    && module.isNamed()
                    && module.getLayer() == ModuleLayer.boot()
                    && ModuleFinder.ofSystem().find(module.getName()).isPresent();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return false;
        }
    }

    private static boolean hasApiOrigin(Class<?> type) {
        if (type.getClassLoader() != YierLoomApi.class.getClassLoader()) {
            return false;
        }
        try {
            CodeSource expected = YierLoomApi.class.getProtectionDomain().getCodeSource();
            CodeSource actual = type.getProtectionDomain().getCodeSource();
            if (expected != null || actual != null) {
                return Objects.equals(expected, actual);
            }
            return type.getProtectionDomain() == YierLoomApi.class.getProtectionDomain();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return false;
        }
    }

    private static boolean hasPrefix(String value, List<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }

    private static String methodLocation(Class<?> adviceClass, Method method) {
        return adviceClass.getName() + "#" + method.getName() + Type.getMethodDescriptor(method);
    }

    private static String firstOrNull(List<String> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    private static byte[] classBytes(ClassLoader owner, String binaryName) throws IOException {
        String resource = binaryName.replace('.', '/') + ".class";
        InputStream supplied = owner == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : owner.getResourceAsStream(resource);
        if (supplied == null) {
            return null;
        }
        try (InputStream input = supplied) {
            return input.readAllBytes();
        }
    }

    private static void close(
            AutoCloseable resource,
            String location,
            Collector violations,
            FatalAccumulator fatal
    ) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Throwable failure) {
            if (!fatal.record(failure)) {
                violations.error("VERIFIER_CLEANUP", location, failure.getClass().getName());
            }
        }
    }

    private static void rethrowFatal(Throwable failure) {
        Throwable fatal = fatalCause(failure);
        if (fatal instanceof VirtualMachineError error) {
            throw error;
        }
        if (fatal instanceof ThreadDeath error) {
            throw error;
        }
    }

    private static Throwable fatalCause(Throwable failure) {
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
            enqueueCause(current, pending);
            enqueueSuppressed(current, pending);
        }
        return null;
    }

    private static void enqueueCause(
            Throwable current,
            ArrayDeque<Throwable> pending
    ) {
        try {
            enqueue(current.getCause(), pending);
        } catch (Throwable accessorFailure) {
            enqueue(accessorFailure, pending);
        }
    }

    private static void enqueueSuppressed(
            Throwable current,
            ArrayDeque<Throwable> pending
    ) {
        try {
            Throwable[] suppressed = current.getSuppressed();
            if (suppressed == null) {
                return;
            }
            for (Throwable candidate : suppressed) {
                enqueue(candidate, pending);
            }
        } catch (Throwable accessorFailure) {
            enqueue(accessorFailure, pending);
        }
    }

    private static void enqueue(
            Throwable candidate,
            ArrayDeque<Throwable> pending
    ) {
        if (candidate != null) {
            pending.addLast(candidate);
        }
    }

    @FunctionalInterface
    private interface ClassBytes {
        byte[] read(String binaryName) throws Exception;
    }

    private record Version(int major, int minor) {
    }

    private record TypePlan(String location, AgentBuilder.Transformer transformer) {
    }

    private record ModulePlan(String id, Set<String> helpers, List<TypePlan> types) {
        private ModulePlan {
            helpers = Set.copyOf(helpers);
            types = List.copyOf(types);
        }
    }

    private static final class Collector {
        private final List<PluginViolation> violations = new ArrayList<>();

        private void error(String code, String location, String detail) {
            violations.add(new PluginViolation(
                    PluginViolationSeverity.ERROR, code, location, detail));
        }

        private void info(String code, String location, String detail) {
            violations.add(new PluginViolation(
                    PluginViolationSeverity.INFO, code, location, detail));
        }

        private void addAll(List<PluginViolation> additions) {
            violations.addAll(additions);
        }

        private PluginContractReport report() {
            return new PluginContractReport(violations);
        }
    }

    private static final class FatalAccumulator {
        private Throwable fatal;

        private boolean record(Throwable failure) {
            Throwable candidate = fatalCause(failure);
            if (candidate == null) {
                return false;
            }
            if (fatal == null) {
                fatal = candidate;
            }
            return true;
        }

        private void rethrow() {
            if (fatal instanceof VirtualMachineError error) {
                throw error;
            }
            if (fatal instanceof ThreadDeath error) {
                throw error;
            }
        }
    }
}
