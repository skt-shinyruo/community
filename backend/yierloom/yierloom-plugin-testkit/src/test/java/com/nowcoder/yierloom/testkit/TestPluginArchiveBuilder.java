package com.nowcoder.yierloom.testkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.nowcoder.yierloom.api.YierLoomPlugin;

final class TestPluginArchiveBuilder {
    private static final String SERVICE_ENTRY =
            "META-INF/services/" + YierLoomPlugin.class.getName();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final Path directory;

    TestPluginArchiveBuilder(Path directory) {
        this.directory = directory;
    }

    Path validCombinedPlugin(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid());
    }

    Path adviceWithoutSuppression(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withAdviceAnnotation("@Advice.OnMethodEnter"));
    }

    Path nonInlineAdvice(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withAdviceAnnotation(
                        "@Advice.OnMethodEnter(suppress = Throwable.class, inline = false)"));
    }

    Path adviceSelfMethodReference(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withAdviceMembers("static void local() { }\n")
                .withAdviceBody("local();"));
    }

    Path adviceSelfFieldReference(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withAdviceMembers("static int state;\n")
                .withAdviceBody("state++;"));
    }

    Path adviceSelfClassReference(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withAdviceBody("Class<?> type = SampleAdvice.class;"));
    }

    Path missingTransitiveHelper(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withHelperBody("ExtraHelper.touch();")
                .withAdditionalTypes("final class ExtraHelper { static void touch() { } }\n"));
    }

    Path helperReferencingProvider(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withHelperBody("String ignored = SamplePlugin.class.getName();"));
    }

    Path helperReferencingCore(String fileName) throws IOException {
        CombinedOptions options = CombinedOptions.valid()
                .withHelperBody("com.nowcoder.yierloom.core.Hidden.touch();")
                .withExcludedClasses(Set.of("com.nowcoder.yierloom.core.Hidden"));
        Map<String, String> additional = Map.of(
                "com.nowcoder.yierloom.core.Hidden", """
                        package com.nowcoder.yierloom.core;
                        public final class Hidden {
                            public static void touch() { }
                        }
                        """);
        return combinedPlugin(fileName, options, additional);
    }

    Path helperReferencingAbsentJavaxType(String fileName) throws IOException {
        CombinedOptions options = CombinedOptions.valid()
                .withHelperBody("javax.servlet.Missing.touch();")
                .withExcludedClasses(Set.of("javax.servlet.Missing"));
        Map<String, String> additional = Map.of(
                "javax.servlet.Missing", """
                        package javax.servlet;
                        public final class Missing {
                            public static void touch() { }
                        }
                        """);
        return combinedPlugin(fileName, options, additional);
    }

    Path helperReferencingAbsentApiType(String fileName) throws IOException {
        CombinedOptions options = CombinedOptions.valid()
                .withHelperBody("com.nowcoder.yierloom.api.Missing.touch();")
                .withExcludedClasses(Set.of("com.nowcoder.yierloom.api.Missing"));
        Map<String, String> additional = Map.of(
                "com.nowcoder.yierloom.api.Missing", """
                        package com.nowcoder.yierloom.api;
                        public final class Missing {
                            public static void touch() { }
                        }
                        """);
        return combinedPlugin(fileName, options, additional);
    }

    Path helperReferencingWrongSourceApiType(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withHelperBody("com.nowcoder.yierloom.api.FakeApiType.touch();"));
    }

    Path duplicateModules(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withModules("List.of(new SampleModule(\"sample-module\"), "
                        + "new SampleModule(\"sample-module\"))"));
    }

    Path incompatibleApi(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid().withApiVersion("2.0.0"));
    }

    Path customTransformer(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withTransformer("(builder, type, loader, module, domain) -> builder"));
    }

    Path validBinding(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withBindingAnnotation("""
                        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                        @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
                        @interface Bound { }
                        """)
                .withAdviceParameter("@Bound String value")
                .withBinding(", new AdviceBinding(Bound.class, \"bound\")"));
    }

    Path invalidBindingRetention(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withBindingAnnotation("""
                        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                        @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
                        @interface Bound { }
                        """)
                .withAdviceParameter("@Bound String value")
                .withBinding(", new AdviceBinding(Bound.class, \"bound\")"));
    }

    Path pluginClassBindingValue(String fileName) throws IOException {
        return combinedPlugin(fileName, classBinding("SamplePlugin.class"));
    }

    Path validJdkArrayBindingValue(String fileName) throws IOException {
        return combinedPlugin(fileName, classBinding("String[][].class"));
    }

    Path validApiArrayBindingValue(String fileName) throws IOException {
        return combinedPlugin(fileName, classBinding("PluginObservation[].class"));
    }

    Path sdkClassBindingValue(String fileName) throws IOException {
        return combinedPlugin(fileName, classBinding("AdviceBinding.class"));
    }

    Path sdkArrayBindingValue(String fileName) throws IOException {
        return combinedPlugin(fileName, classBinding("AdviceBinding[][].class"));
    }

    Path byteBuddyEnumBindingValue(String fileName) throws IOException {
        return combinedPlugin(fileName, CombinedOptions.valid()
                .withBindingAnnotation(validBindingAnnotation())
                .withAdviceParameter(
                        "@Bound net.bytebuddy.description.modifier.Visibility value")
                .withBinding(", new AdviceBinding(Bound.class, "
                        + "net.bytebuddy.description.modifier.Visibility.PUBLIC)"));
    }

    Path rootOnlyProvider(String fileName) throws IOException {
        String className = "fixture.RootOnlyPlugin";
        return jar(
                fileName,
                Map.of(className, rootOnlySource("RootOnlyPlugin", "1.0.0")),
                List.of(className),
                Set.of(),
                Map.of());
    }

    Path providerMissingDependencyFromItsOwnJar(String fileName) throws IOException {
        String className = "fixture.ParentFallbackPlugin";
        String source = """
                package fixture;
                import com.nowcoder.yierloom.api.*;
                import com.nowcoder.yierloom.testkit.PluginContractVerifierTest.ParentOnlyDependency;
                public final class ParentFallbackPlugin
                        implements YierLoomPlugin, RuntimeCapability {
                    public PluginDescriptor descriptor() {
                        return new PluginDescriptor(
                                "sample", ParentOnlyDependency.value(), "1.0.0", "1.0.0", true, 0);
                    }
                    public void start(PluginRuntimeContext context) { }
                    public void stop() { }
                }
                """;
        return jar(fileName, Map.of(className, source), List.of(className), Set.of(), Map.of());
    }

    Path providerBundlingFakeJdkClass(String fileName) throws IOException {
        String className = "fixture.FakeJdkPlugin";
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(className, """
                package fixture;
                import com.nowcoder.yierloom.api.*;
                public final class FakeJdkPlugin implements YierLoomPlugin, RuntimeCapability {
                    public PluginDescriptor descriptor() {
                        return new PluginDescriptor(
                                "sample", sun.fake.Shadow.value(), "1.0.0", "1.0.0", true, 0);
                    }
                    public void start(PluginRuntimeContext context) { }
                    public void stop() { }
                }
                """);
        sources.put("sun.fake.Shadow", """
                package sun.fake;
                public final class Shadow {
                    public static String value() { return "fake JDK type"; }
                }
                """);
        return jar(fileName, sources, List.of(className), Set.of(), Map.of());
    }

    Path twoProviders(String fileName) throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture.FirstPlugin", rootOnlySource("FirstPlugin", "1.0.0"));
        sources.put("fixture.SecondPlugin", rootOnlySource("SecondPlugin", "1.0.0"));
        return jar(
                fileName,
                sources,
                List.of("fixture.FirstPlugin", "fixture.SecondPlugin"),
                Set.of(),
                Map.of());
    }

    Path withForbiddenEntry(String fileName, String entry) throws IOException {
        return jar(fileName, Map.of(), List.of(), Set.of(), Map.of(entry, new byte[]{0}));
    }

    private Path combinedPlugin(String fileName, CombinedOptions options) throws IOException {
        return combinedPlugin(fileName, options, Map.of());
    }

    private Path combinedPlugin(
            String fileName,
            CombinedOptions options,
            Map<String, String> additionalSources
    ) throws IOException {
        String className = "fixture.SamplePlugin";
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(className, combinedSource(options));
        sources.putAll(additionalSources);
        return jar(
                fileName,
                sources,
                List.of(className),
                options.excludedClasses(),
                Map.of());
    }

    private Path jar(
            String fileName,
            Map<String, String> sources,
            List<String> providers,
            Set<String> excludedClasses,
            Map<String, byte[]> additionalEntries
    ) throws IOException {
        Files.createDirectories(directory);
        Path workspace = Files.createDirectories(
                directory.resolve("fixture-" + SEQUENCE.incrementAndGet()));
        Path sourceDirectory = Files.createDirectories(workspace.resolve("src"));
        Path classesDirectory = Files.createDirectories(workspace.resolve("classes"));
        List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = sourceDirectory.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
            sourceFiles.add(file);
        }
        compile(sourceFiles, classesDirectory);

        Path jar = directory.resolve(fileName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            try (var files = Files.walk(classesDirectory)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String name = classesDirectory.relativize(file).toString().replace('\\', '/');
                    if (!excluded(name, excludedClasses)) {
                        addEntry(output, name, Files.readAllBytes(file));
                    }
                }
            }
            for (Map.Entry<String, byte[]> entry : additionalEntries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList()) {
                addEntry(output, entry.getKey(), entry.getValue());
            }
            String declaration = String.join("\n", providers)
                    + (providers.isEmpty() ? "" : "\n");
            addEntry(output, SERVICE_ENTRY, declaration.getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }

    private static boolean excluded(String entry, Set<String> excludedClasses) {
        return excludedClasses.stream()
                .map(name -> name.replace('.', '/'))
                .anyMatch(prefix -> entry.equals(prefix + ".class")
                        || entry.startsWith(prefix + "$"));
    }

    private static void compile(List<Path> sourceFiles, Path classesDirectory) throws IOException {
        if (sourceFiles.isEmpty()) {
            return;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("tests require a JDK");
        }
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                null, null, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(
                    sourceFiles.stream().sorted(Comparator.naturalOrder()).toList());
            List<String> options = List.of(
                    "--release", "17",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classesDirectory.toString());
            if (!compiler.getTask(null, files, null, options, null, units).call()) {
                throw new IllegalStateException("fixture compilation failed");
            }
        }
    }

    private static void addEntry(JarOutputStream output, String name, byte[] value)
            throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(value);
        output.closeEntry();
    }

    private static String rootOnlySource(String simpleName, String apiVersion) {
        return """
                package fixture;
                import com.nowcoder.yierloom.api.*;
                public final class %s implements YierLoomPlugin {
                    public PluginDescriptor descriptor() {
                        return new PluginDescriptor("sample", "Sample", "1.0.0", "%s", true, 0);
                    }
                }
                """.formatted(simpleName, apiVersion);
    }

    private static CombinedOptions classBinding(String value) {
        return CombinedOptions.valid()
                .withBindingAnnotation(validBindingAnnotation())
                .withAdviceParameter("@Bound Class<?> value")
                .withBinding(", new AdviceBinding(Bound.class, " + value + ")");
    }

    private static String validBindingAnnotation() {
        return """
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
                @interface Bound { }
                """;
    }

    private static String combinedSource(CombinedOptions options) {
        return """
                package fixture;

                import java.time.Duration;
                import java.util.List;
                import java.util.Set;
                import com.nowcoder.yierloom.api.*;
                import com.nowcoder.yierloom.sdk.*;
                import net.bytebuddy.agent.builder.AgentBuilder;
                import net.bytebuddy.asm.Advice;
                import net.bytebuddy.description.type.TypeDescription;
                import net.bytebuddy.matcher.ElementMatcher;
                import net.bytebuddy.matcher.ElementMatchers;

                public final class SamplePlugin
                        implements YierLoomPlugin, RuntimeCapability, InstrumentationCapability {
                    public PluginDescriptor descriptor() {
                        return new PluginDescriptor(
                                "sample", "Sample", "1.0.0", "%s", true, 0);
                    }
                    public void start(PluginRuntimeContext context) {
                        context.observations().register(observation -> { });
                        context.scheduler().scheduleWithFixedDelay(
                                "sample-task", Duration.ZERO, Duration.ofSeconds(1), () -> { });
                    }
                    public void stop() { }
                    public List<InstrumentationModule> instrumentations(PluginConfig config) {
                        return %s;
                    }
                }

                final class SampleModule implements InstrumentationModule {
                    private final String id;
                    SampleModule(String id) { this.id = id; }
                    public String id() { return id; }
                    public List<? extends TypeInstrumentation> typeInstrumentations() {
                        return List.of(new SampleTypeInstrumentation());
                    }
                    public Set<String> helperClassNames() {
                        return Set.of("fixture.SampleHelper");
                    }
                }

                final class SampleTypeInstrumentation implements TypeInstrumentation {
                    public ElementMatcher<? super TypeDescription> typeMatcher() {
                        return ElementMatchers.any();
                    }
                    public AgentBuilder.Transformer transformer() {
                        return %s;
                    }
                }

                %s
                final class SampleAdvice {
                    %s
                    %s
                    static void enter(%s) { %s }
                }

                final class SampleHelper {
                    static void observe() {
                        %s
                    }
                }

                %s
                """.formatted(
                options.apiVersion(),
                options.modules(),
                transformer(options),
                options.bindingAnnotation(),
                options.adviceMembers(),
                options.adviceAnnotation(),
                options.adviceParameter(),
                options.adviceBody(),
                options.helperBody(),
                options.additionalTypes());
    }

    private static String transformer(CombinedOptions options) {
        if (!options.transformer().isEmpty()) {
            return options.transformer();
        }
        return "AdviceTransformers.forAdvice(SampleAdvice.class, ElementMatchers.any()"
                + options.binding() + ")";
    }

    private record CombinedOptions(
            String apiVersion,
            String modules,
            String transformer,
            String bindingAnnotation,
            String binding,
            String adviceMembers,
            String adviceAnnotation,
            String adviceParameter,
            String adviceBody,
            String helperBody,
            String additionalTypes,
            Set<String> excludedClasses
    ) {
        private static CombinedOptions valid() {
            return new CombinedOptions(
                    "1.0.0",
                    "List.of(new SampleModule(\"sample-module\"))",
                    "",
                    "",
                    "",
                    "",
                    "@Advice.OnMethodEnter(suppress = Throwable.class)",
                    "",
                    "SampleHelper.observe();",
                    "YierLoomBridge.observe(\"sample\", "
                            + "PluginObservation.builder(\"sample\").build());",
                    "",
                    Set.of());
        }

        private CombinedOptions withApiVersion(String value) {
            return copy(value, modules, transformer, bindingAnnotation, binding, adviceMembers,
                    adviceAnnotation, adviceParameter, adviceBody, helperBody, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withModules(String value) {
            return copy(apiVersion, value, transformer, bindingAnnotation, binding, adviceMembers,
                    adviceAnnotation, adviceParameter, adviceBody, helperBody, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withTransformer(String value) {
            return copy(apiVersion, modules, value, bindingAnnotation, binding, adviceMembers,
                    adviceAnnotation, adviceParameter, adviceBody, helperBody, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withBindingAnnotation(String value) {
            return copy(apiVersion, modules, transformer, value, binding, adviceMembers,
                    adviceAnnotation, adviceParameter, adviceBody, helperBody, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withBinding(String value) {
            return copy(apiVersion, modules, transformer, bindingAnnotation, value, adviceMembers,
                    adviceAnnotation, adviceParameter, adviceBody, helperBody, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withAdviceMembers(String value) {
            return copy(apiVersion, modules, transformer, bindingAnnotation, binding, value,
                    adviceAnnotation, adviceParameter, adviceBody, helperBody, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withAdviceAnnotation(String value) {
            return copy(apiVersion, modules, transformer, bindingAnnotation, binding, adviceMembers,
                    value, adviceParameter, adviceBody, helperBody, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withAdviceParameter(String value) {
            return copy(apiVersion, modules, transformer, bindingAnnotation, binding, adviceMembers,
                    adviceAnnotation, value, adviceBody, helperBody, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withAdviceBody(String value) {
            return copy(apiVersion, modules, transformer, bindingAnnotation, binding, adviceMembers,
                    adviceAnnotation, adviceParameter, value, helperBody, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withHelperBody(String value) {
            return copy(apiVersion, modules, transformer, bindingAnnotation, binding, adviceMembers,
                    adviceAnnotation, adviceParameter, adviceBody, value, additionalTypes,
                    excludedClasses);
        }

        private CombinedOptions withAdditionalTypes(String value) {
            return copy(apiVersion, modules, transformer, bindingAnnotation, binding, adviceMembers,
                    adviceAnnotation, adviceParameter, adviceBody, helperBody, value,
                    excludedClasses);
        }

        private CombinedOptions withExcludedClasses(Set<String> value) {
            return copy(apiVersion, modules, transformer, bindingAnnotation, binding, adviceMembers,
                    adviceAnnotation, adviceParameter, adviceBody, helperBody, additionalTypes,
                    value);
        }

        private static CombinedOptions copy(
                String apiVersion,
                String modules,
                String transformer,
                String bindingAnnotation,
                String binding,
                String adviceMembers,
                String adviceAnnotation,
                String adviceParameter,
                String adviceBody,
                String helperBody,
                String additionalTypes,
                Set<String> excludedClasses
        ) {
            return new CombinedOptions(
                    apiVersion, modules, transformer, bindingAnnotation, binding, adviceMembers,
                    adviceAnnotation, adviceParameter, adviceBody, helperBody, additionalTypes,
                    excludedClasses);
        }
    }
}
