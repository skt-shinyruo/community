package com.nowcoder.yierloom.integration.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class TestPluginJarBuilder {
    private static final String SERVICE_ENTRY =
            "META-INF/services/com.nowcoder.yierloom.api.YierLoomPlugin";
    private static final Pattern PLUGIN_ID = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern BINARY_NAME = Pattern.compile(
            "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"
                    + "(?:\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    private final Path workspace;
    private final AtomicInteger sequence = new AtomicInteger();

    public TestPluginJarBuilder() {
        try {
            this.workspace = prepareWorkspace(Path.of("target", "test-plugin-builds"));
        } catch (IOException failure) {
            throw new IllegalStateException("cannot prepare plugin fixture workspace", failure);
        }
    }

    public TestPluginJarBuilder(Path workspace) throws IOException {
        this.workspace = prepareWorkspace(workspace);
    }

    public Path combinedObservationPlugin(
            Path pluginDirectory,
            String id,
            String privateVersion
    ) throws IOException {
        String checkedId = requirePluginId(id);
        return buildCombinedPlugin(
                pluginDirectory.resolve(checkedId + ".jar"), checkedId, privateVersion);
    }

    public Path runtimePlugin(
            Path pluginDirectory,
            String id,
            String privateVersion
    ) throws IOException {
        String checkedId = requirePluginId(id);
        return buildRuntimePlugin(
                pluginDirectory.resolve(checkedId + ".jar"),
                checkedId,
                privateVersion,
                RuntimeFixture.READY,
                null);
    }

    public Path instrumentationOnlyPlugin(Path pluginDirectory, String id) throws IOException {
        String checkedId = requirePluginId(id);
        String packageName = fixturePackage(checkedId);
        Map<String, String> sources = new TreeMap<>();
        sources.put(packageName + ".FixturePlugin", instrumentationProviderSource(
                packageName, checkedId));
        addInstrumentationSources(
                sources, packageName, checkedId, HelperFixture.EMIT_EVENT, null);
        return build(
                pluginDirectory.resolve(checkedId + ".jar"),
                packageName + ".FixturePlugin",
                sources,
                Map.of());
    }

    public Path markerPlugin(
            Path pluginDirectory,
            String id,
            Path startMarker,
            Path transformationMarker
    ) throws IOException {
        String checkedId = requirePluginId(id);
        Path checkedStartMarker = Objects.requireNonNull(startMarker, "startMarker")
                .toAbsolutePath()
                .normalize();
        Path checkedTransformationMarker = Objects.requireNonNull(
                        transformationMarker, "transformationMarker")
                .toAbsolutePath()
                .normalize();
        String packageName = fixturePackage(checkedId);
        Map<String, String> sources = new TreeMap<>();
        sources.put(packageName + ".FixturePlugin", markerProviderSource(
                packageName, checkedId, checkedStartMarker));
        addInstrumentationSources(
                sources,
                packageName,
                checkedId,
                HelperFixture.WRITE_MARKER,
                checkedTransformationMarker);
        return build(
                pluginDirectory.resolve(checkedId + ".jar"),
                packageName + ".FixturePlugin",
                sources,
                Map.of());
    }

    public Path corruptJar(Path outputJar) throws IOException {
        Path target = Objects.requireNonNull(outputJar, "outputJar")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(requireParent(target));
        Files.write(
                target,
                "not-a-yierloom-plugin-jar".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return target;
    }

    public List<Path> duplicateIdPair(Path pluginDirectory, String id) throws IOException {
        String checkedId = requirePluginId(id);
        Path first = buildRuntimePlugin(
                pluginDirectory.resolve("00-" + checkedId + "-one.jar"),
                checkedId,
                "one",
                RuntimeFixture.READY,
                null);
        Path second = buildRuntimePlugin(
                pluginDirectory.resolve("01-" + checkedId + "-two.jar"),
                checkedId,
                "two",
                RuntimeFixture.READY,
                null);
        return List.of(first, second);
    }

    public Path startFailure(Path pluginDirectory, String id) throws IOException {
        String checkedId = requirePluginId(id);
        return buildRuntimePlugin(
                pluginDirectory.resolve(checkedId + ".jar"),
                checkedId,
                "start-failure",
                RuntimeFixture.START_FAILURE,
                null);
    }

    public Path stopFailure(Path pluginDirectory, String id) throws IOException {
        String checkedId = requirePluginId(id);
        return buildRuntimePlugin(
                pluginDirectory.resolve(checkedId + ".jar"),
                checkedId,
                "stop-failure",
                RuntimeFixture.STOP_FAILURE,
                null);
    }

    public Path invalidConfiguration(Path pluginDirectory, String id) throws IOException {
        String checkedId = requirePluginId(id);
        return buildRuntimePlugin(
                pluginDirectory.resolve(checkedId + ".jar"),
                checkedId,
                "invalid-config",
                RuntimeFixture.INVALID_CONFIG,
                null);
    }

    public Path transformationFailure(Path pluginDirectory, String id) throws IOException {
        String checkedId = requirePluginId(id);
        String packageName = fixturePackage(checkedId);
        Map<String, String> sources = new TreeMap<>();
        sources.put(packageName + ".FixturePlugin", instrumentationProviderSource(
                packageName, checkedId));
        sources.put(packageName + ".FixtureModule", failingModuleSource(packageName, checkedId));
        sources.put(packageName + ".FixtureTypeInstrumentation", failingTypeSource(packageName));
        return build(
                pluginDirectory.resolve(checkedId + ".jar"),
                packageName + ".FixturePlugin",
                sources,
                Map.of());
    }

    public Path build(
            Path outputJar,
            String providerClassName,
            Map<String, String> sources
    ) throws IOException {
        return build(outputJar, providerClassName, sources, Map.of());
    }

    public Path build(
            Path outputJar,
            String providerClassName,
            Map<String, String> sources,
            Map<String, byte[]> resources
    ) throws IOException {
        Path target = Objects.requireNonNull(outputJar, "outputJar")
                .toAbsolutePath()
                .normalize();
        String provider = requireBinaryName(providerClassName, "providerClassName");
        Map<String, String> sourceCopy = new TreeMap<>(Objects.requireNonNull(sources, "sources"));
        if (sourceCopy.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        sourceCopy.forEach((name, source) -> {
            requireBinaryName(name, "source binary name");
            Objects.requireNonNull(source, "source");
        });

        Path buildDirectory = Files.createTempDirectory(
                workspace, String.format(Locale.ROOT, "build-%04d-", sequence.incrementAndGet()));
        makePrivate(buildDirectory);
        try {
            Path sourceDirectory = Files.createDirectories(buildDirectory.resolve("sources"));
            Path classesDirectory = Files.createDirectories(buildDirectory.resolve("classes"));
            List<Path> sourceFiles = writeSources(sourceDirectory, sourceCopy);
            compile(sourceFiles, classesDirectory);

            TreeMap<String, byte[]> entries = classEntries(classesDirectory);
            addResources(entries, resources);
            putUnique(
                    entries,
                    SERVICE_ENTRY,
                    (provider + "\n").getBytes(StandardCharsets.UTF_8));
            writeAtomically(target, entries);
            return target;
        } finally {
            deleteTree(buildDirectory);
        }
    }

    private Path buildCombinedPlugin(Path output, String id, String privateVersion)
            throws IOException {
        Objects.requireNonNull(privateVersion, "privateVersion");
        String packageName = fixturePackage(id);
        Map<String, String> sources = new TreeMap<>();
        sources.put(packageName + ".FixturePlugin", combinedProviderSource(packageName, id));
        sources.put("fixture.privatecopy.Version", privateVersionSource(privateVersion));
        addInstrumentationSources(
                sources, packageName, id, HelperFixture.OBSERVE, null);
        return build(output, packageName + ".FixturePlugin", sources, Map.of());
    }

    private Path buildRuntimePlugin(
            Path output,
            String id,
            String privateVersion,
            RuntimeFixture fixture,
            Path marker
    ) throws IOException {
        Objects.requireNonNull(privateVersion, "privateVersion");
        String packageName = fixturePackage(id);
        Map<String, String> sources = new TreeMap<>();
        sources.put(packageName + ".FixturePlugin", runtimeProviderSource(
                packageName, id, fixture, marker));
        sources.put("fixture.privatecopy.Version", privateVersionSource(privateVersion));
        return build(output, packageName + ".FixturePlugin", sources, Map.of());
    }

    private static void addInstrumentationSources(
            Map<String, String> sources,
            String packageName,
            String id,
            HelperFixture helperFixture,
            Path marker
    ) {
        sources.put(packageName + ".FixtureModule", moduleSource(packageName, id));
        sources.put(packageName + ".FixtureTypeInstrumentation", typeSource(packageName));
        sources.put(packageName + ".FixtureAdvice", adviceSource(packageName));
        sources.put(packageName + ".FixtureHelper", helperSource(
                packageName, id, helperFixture, marker));
    }

    private static List<Path> writeSources(
            Path sourceDirectory,
            Map<String, String> sources
    ) throws IOException {
        List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path path = sourceDirectory.resolve(
                            source.getKey().replace('.', '/') + ".java")
                    .normalize();
            if (!path.startsWith(sourceDirectory)) {
                throw new IllegalArgumentException("source path escapes build directory");
            }
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    source.getValue(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            sourceFiles.add(path);
        }
        return List.copyOf(sourceFiles);
    }

    private static void compile(List<Path> sourceFiles, Path classesDirectory) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("a JDK JavaCompiler is required for plugin fixtures");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            List<String> options = List.of(
                    "--release", "17",
                    "-proc:none",
                    "-encoding", StandardCharsets.UTF_8.name(),
                    "-g:none",
                    "-classpath", compilationClasspath(),
                    "-d", classesDirectory.toString());
            boolean successful = Boolean.TRUE.equals(compiler.getTask(
                    null, fileManager, diagnostics, options, null, units).call());
            if (!successful) {
                throw new IllegalStateException(formatDiagnostics(diagnostics));
            }
        }
    }

    private static String compilationClasspath() {
        String classpath = System.getProperty("surefire.test.class.path");
        if (classpath == null || classpath.isBlank()) {
            classpath = System.getProperty("java.class.path");
        }
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalStateException("plugin compilation classpath is unavailable");
        }
        return classpath;
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder message = new StringBuilder("plugin fixture compilation failed");
        diagnostics.getDiagnostics().stream()
                .sorted(Comparator.comparingLong(Diagnostic::getLineNumber))
                .forEach(diagnostic -> message
                        .append(System.lineSeparator())
                        .append(diagnostic.getKind())
                        .append(" line ").append(diagnostic.getLineNumber())
                        .append(": ").append(diagnostic.getMessage(Locale.ROOT)));
        return message.toString();
    }

    private static TreeMap<String, byte[]> classEntries(Path classesDirectory) throws IOException {
        TreeMap<String, byte[]> entries = new TreeMap<>();
        try (Stream<Path> files = Files.walk(classesDirectory)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = classesDirectory.relativize(path).toString().replace('\\', '/');
                putUnique(entries, name, Files.readAllBytes(path));
            }
        }
        return entries;
    }

    private static void addResources(
            TreeMap<String, byte[]> entries,
            Map<String, byte[]> resources
    ) {
        Objects.requireNonNull(resources, "resources").entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(resource -> {
                    String name = requireEntryName(resource.getKey());
                    if (SERVICE_ENTRY.equals(name)) {
                        throw new IllegalArgumentException("service declaration is builder-owned");
                    }
                    putUnique(
                            entries,
                            name,
                            Objects.requireNonNull(resource.getValue(), "resource bytes").clone());
                });
    }

    private static void writeAtomically(Path target, TreeMap<String, byte[]> entries)
            throws IOException {
        Path parent = requireParent(target);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".yierloom-plugin-", ".jar.tmp");
        try {
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(
                    temporary,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE))) {
                for (Map.Entry<String, byte[]> candidate : entries.entrySet()) {
                    byte[] bytes = candidate.getValue();
                    CRC32 crc = new CRC32();
                    crc.update(bytes);
                    JarEntry entry = new JarEntry(candidate.getKey());
                    entry.setMethod(JarEntry.STORED);
                    entry.setTime(0L);
                    entry.setSize(bytes.length);
                    entry.setCompressedSize(bytes.length);
                    entry.setCrc(crc.getValue());
                    jar.putNextEntry(entry);
                    jar.write(bytes);
                    jar.closeEntry();
                }
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String requireEntryName(String supplied) {
        String name = Objects.requireNonNull(supplied, "resource name");
        if (name.isBlank()
                || name.startsWith("/")
                || name.endsWith("/")
                || name.indexOf('\\') >= 0
                || Stream.of(name.split("/", -1)).anyMatch(part -> part.isEmpty() || "..".equals(part))) {
            throw new IllegalArgumentException("invalid JAR entry name: " + name);
        }
        return name;
    }

    private static void putUnique(Map<String, byte[]> entries, String name, byte[] bytes) {
        if (entries.putIfAbsent(name, bytes) != null) {
            throw new IllegalArgumentException("duplicate JAR entry: " + name);
        }
    }

    private static Path requireParent(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("path has no parent: " + path);
        }
        return parent;
    }

    private static void makePrivate(Path directory) throws IOException {
        try {
            Files.setPosixFilePermissions(directory, PRIVATE_DIRECTORY_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String requirePluginId(String id) {
        String checked = Objects.requireNonNull(id, "id");
        if (!PLUGIN_ID.matcher(checked).matches()) {
            throw new IllegalArgumentException("invalid plugin id: " + checked);
        }
        return checked;
    }

    private static String requireBinaryName(String name, String parameter) {
        String checked = Objects.requireNonNull(name, parameter);
        if (!BINARY_NAME.matcher(checked).matches()) {
            throw new IllegalArgumentException("invalid binary name: " + checked);
        }
        return checked;
    }

    private static String fixturePackage(String id) {
        return "fixture.generated." + id.replace('-', '_');
    }

    private static String privateVersionSource(String version) {
        return """
                package fixture.privatecopy;

                public final class Version {
                    private Version() {}

                    public static String value() {
                        return %s;
                    }
                }
                """.formatted(javaLiteral(version));
    }

    private static String combinedProviderSource(String packageName, String id) {
        return """
                package %s;

                import java.util.List;
                import com.nowcoder.yierloom.api.DiagnosticEvent;
                import com.nowcoder.yierloom.api.PluginConfig;
                import com.nowcoder.yierloom.api.PluginDescriptor;
                import com.nowcoder.yierloom.api.PluginRuntimeContext;
                import com.nowcoder.yierloom.api.RuntimeCapability;
                import com.nowcoder.yierloom.api.YierLoomApi;
                import com.nowcoder.yierloom.api.YierLoomPlugin;
                import com.nowcoder.yierloom.sdk.InstrumentationCapability;
                import com.nowcoder.yierloom.sdk.InstrumentationModule;

                public final class FixturePlugin
                        implements YierLoomPlugin, RuntimeCapability, InstrumentationCapability {
                    private static final String ID = %s;
                    private boolean started;

                    public PluginDescriptor descriptor() {
                        return new PluginDescriptor(ID, ID, "1.0.0", YierLoomApi.VERSION, true, 500);
                    }

                    public synchronized void start(PluginRuntimeContext context) {
                        if (started) {
                            throw new IllegalStateException("fixture already started");
                        }
                        context.observations().register(observation ->
                                context.events().emit(DiagnosticEvent.builder(%s)
                                        .attribute("fixture.private.version", fixture.privatecopy.Version.value())
                                        .attribute("fixture.target.class", observation.attributes()
                                                .getOrDefault("fixture.target.class", "-"))
                                        .attribute("fixture.helper.loader", observation.attributes()
                                                .getOrDefault("fixture.helper.loader", "-"))
                                        .attribute("fixture.bridge.loader", observation.attributes()
                                                .getOrDefault("fixture.bridge.loader", "-"))
                                        .longField("seen", observation.longFields()
                                                .getOrDefault("value", 0L))
                                        .build()));
                        started = true;
                    }

                    public List<InstrumentationModule> instrumentations(PluginConfig config) {
                        return List.of(new FixtureModule());
                    }

                    public synchronized void stop() {
                        started = false;
                    }
                }
                """.formatted(
                packageName,
                javaLiteral(id),
                javaLiteral(actionPrefix(id) + "_summary"));
    }

    private static String runtimeProviderSource(
            String packageName,
            String id,
            RuntimeFixture fixture,
            Path marker
    ) {
        String startBody = switch (fixture) {
            case READY -> """
                    if (started) {
                        throw new IllegalStateException("fixture already started");
                    }
                    context.events().emit(DiagnosticEvent.builder(%s)
                            .attribute("fixture.private.version", fixture.privatecopy.Version.value())
                            .build());
                    started = true;
                    """.formatted(javaLiteral(actionPrefix(id) + "_ready"));
            case START_FAILURE -> "throw new IllegalStateException(\"fixture start failure\");";
            case STOP_FAILURE -> """
                    context.events().emit(DiagnosticEvent.builder(%s)
                            .attribute("fixture.private.version", fixture.privatecopy.Version.value())
                            .build());
                    started = true;
                    """.formatted(javaLiteral(actionPrefix(id) + "_ready"));
            case INVALID_CONFIG -> """
                    context.config().requireInt("limit");
                    context.events().emit(DiagnosticEvent.builder(%s).build());
                    started = true;
                    """.formatted(javaLiteral(actionPrefix(id) + "_ready"));
        };
        if (marker != null) {
            startBody = "java.nio.file.Files.writeString(java.nio.file.Path.of("
                    + javaLiteral(marker.toString()) + "), \"started\");\n" + startBody;
        }
        String stopBody = fixture == RuntimeFixture.STOP_FAILURE
                ? "System.out.println(\"STOP_ATTEMPTED:\" + ID);\n"
                        + "throw new IllegalStateException(\"fixture stop failure\");"
                : "started = false;";
        return """
                package %s;

                import com.nowcoder.yierloom.api.DiagnosticEvent;
                import com.nowcoder.yierloom.api.PluginDescriptor;
                import com.nowcoder.yierloom.api.PluginRuntimeContext;
                import com.nowcoder.yierloom.api.RuntimeCapability;
                import com.nowcoder.yierloom.api.YierLoomApi;
                import com.nowcoder.yierloom.api.YierLoomPlugin;

                public final class FixturePlugin implements YierLoomPlugin, RuntimeCapability {
                    private static final String ID = %s;
                    private boolean started;

                    public PluginDescriptor descriptor() {
                        return new PluginDescriptor(ID, ID, "1.0.0", YierLoomApi.VERSION, true, 500);
                    }

                    public synchronized void start(PluginRuntimeContext context) throws Exception {
                        %s
                    }

                    public synchronized void stop() {
                        %s
                    }
                }
                """.formatted(packageName, javaLiteral(id), startBody, stopBody);
    }

    private static String instrumentationProviderSource(String packageName, String id) {
        return """
                package %s;

                import java.util.List;
                import com.nowcoder.yierloom.api.PluginConfig;
                import com.nowcoder.yierloom.api.PluginDescriptor;
                import com.nowcoder.yierloom.api.YierLoomApi;
                import com.nowcoder.yierloom.api.YierLoomPlugin;
                import com.nowcoder.yierloom.sdk.InstrumentationCapability;
                import com.nowcoder.yierloom.sdk.InstrumentationModule;

                public final class FixturePlugin implements YierLoomPlugin, InstrumentationCapability {
                    private static final String ID = %s;

                    public PluginDescriptor descriptor() {
                        return new PluginDescriptor(ID, ID, "1.0.0", YierLoomApi.VERSION, true, 500);
                    }

                    public List<InstrumentationModule> instrumentations(PluginConfig config) {
                        return List.of(new FixtureModule());
                    }
                }
                """.formatted(packageName, javaLiteral(id));
    }

    private static String markerProviderSource(String packageName, String id, Path startMarker) {
        return """
                package %s;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.List;
                import com.nowcoder.yierloom.api.DiagnosticEvent;
                import com.nowcoder.yierloom.api.PluginConfig;
                import com.nowcoder.yierloom.api.PluginDescriptor;
                import com.nowcoder.yierloom.api.PluginRuntimeContext;
                import com.nowcoder.yierloom.api.RuntimeCapability;
                import com.nowcoder.yierloom.api.YierLoomApi;
                import com.nowcoder.yierloom.api.YierLoomPlugin;
                import com.nowcoder.yierloom.sdk.InstrumentationCapability;
                import com.nowcoder.yierloom.sdk.InstrumentationModule;

                public final class FixturePlugin
                        implements YierLoomPlugin, RuntimeCapability, InstrumentationCapability {
                    private static final String ID = %s;

                    public PluginDescriptor descriptor() {
                        return new PluginDescriptor(ID, ID, "1.0.0", YierLoomApi.VERSION, true, 500);
                    }

                    public void start(PluginRuntimeContext context) throws Exception {
                        Files.writeString(Path.of(%s), "started");
                        context.events().emit(DiagnosticEvent.builder(%s).build());
                    }

                    public List<InstrumentationModule> instrumentations(PluginConfig config) {
                        return List.of(new FixtureModule());
                    }

                    public void stop() {}
                }
                """.formatted(
                packageName,
                javaLiteral(id),
                javaLiteral(startMarker.toString()),
                javaLiteral(actionPrefix(id) + "_started"));
    }

    private static String moduleSource(String packageName, String id) {
        return """
                package %s;

                import java.util.List;
                import java.util.Set;
                import com.nowcoder.yierloom.sdk.InstrumentationModule;
                import com.nowcoder.yierloom.sdk.TypeInstrumentation;

                public final class FixtureModule implements InstrumentationModule {
                    public String id() {
                        return %s;
                    }

                    public List<? extends TypeInstrumentation> typeInstrumentations() {
                        return List.of(new FixtureTypeInstrumentation());
                    }

                    public Set<String> helperClassNames() {
                        return Set.of(FixtureHelper.class.getName());
                    }
                }
                """.formatted(packageName, javaLiteral(id + "-instrumentation"));
    }

    private static String typeSource(String packageName) {
        return """
                package %s;

                import com.nowcoder.yierloom.sdk.AdviceTransformers;
                import com.nowcoder.yierloom.sdk.TypeInstrumentation;
                import net.bytebuddy.agent.builder.AgentBuilder;
                import net.bytebuddy.description.type.TypeDescription;
                import net.bytebuddy.matcher.ElementMatcher;
                import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
                import static net.bytebuddy.matcher.ElementMatchers.named;

                public final class FixtureTypeInstrumentation implements TypeInstrumentation {
                    public ElementMatcher<? super TypeDescription> typeMatcher() {
                        return nameStartsWith("com.example.yierloom.integration.");
                    }

                    public AgentBuilder.Transformer transformer() {
                        return AdviceTransformers.forAdvice(
                                FixtureAdvice.class,
                                named("fast")
                                        .or(named("slow"))
                                        .or(named("throwsTargetBoom"))
                                        .or(named("work")));
                    }
                }
                """.formatted(packageName);
    }

    private static String adviceSource(String packageName) {
        return """
                package %s;

                import net.bytebuddy.asm.Advice;

                public final class FixtureAdvice {
                    private FixtureAdvice() {}

                    @Advice.OnMethodEnter(suppress = Throwable.class)
                    public static long enter() {
                        return System.nanoTime();
                    }

                    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
                    public static void exit(
                            @Advice.Origin("#t") String targetClass,
                            @Advice.Enter long startedAt
                    ) {
                        FixtureHelper.onExit(
                                targetClass,
                                Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L));
                    }
                }
                """.formatted(packageName);
    }

    private static String helperSource(
            String packageName,
            String id,
            HelperFixture fixture,
            Path marker
    ) {
        String dispatch = switch (fixture) {
            case OBSERVE -> """
                    YierLoomBridge.observe(ID, PluginObservation.builder("fixture-call")
                            .attribute("fixture.target.class", targetClass)
                            .attribute("fixture.helper.loader", helperLoader)
                            .attribute("fixture.bridge.loader", bridgeLoader)
                            .longField("value", 1L)
                            .longField("duration.ms", durationMs)
                            .build());
                    """;
            case EMIT_EVENT -> """
                    YierLoomBridge.emit(ID, DiagnosticEvent.builder(%s)
                            .attribute("fixture.target.class", targetClass)
                            .attribute("fixture.helper.loader", helperLoader)
                            .attribute("fixture.bridge.loader", bridgeLoader)
                            .longField("duration.ms", durationMs)
                            .build());
                    """.formatted(javaLiteral(actionPrefix(id) + "_hit"));
            case WRITE_MARKER -> """
                    try {
                        Files.writeString(Path.of(%s), "transformed");
                    } catch (Exception ignored) {
                    }
                    YierLoomBridge.emit(ID, DiagnosticEvent.builder(%s)
                            .attribute("fixture.target.class", targetClass)
                            .build());
                    """.formatted(
                    javaLiteral(Objects.requireNonNull(marker, "marker").toString()),
                    javaLiteral(actionPrefix(id) + "_transformed"));
        };
        String extraImports = fixture == HelperFixture.OBSERVE
                ? "import com.nowcoder.yierloom.api.PluginObservation;"
                : "import com.nowcoder.yierloom.api.DiagnosticEvent;";
        if (fixture == HelperFixture.WRITE_MARKER) {
            extraImports += "\nimport java.nio.file.Files;\nimport java.nio.file.Path;";
        }
        return """
                package %s;

                %s
                import com.nowcoder.yierloom.api.YierLoomBridge;

                public final class FixtureHelper {
                    private static final String ID = %s;

                    private FixtureHelper() {}

                    public static void onExit(String targetClass, long durationMs) {
                        ClassLoader helperOwner = FixtureHelper.class.getClassLoader();
                        ClassLoader bridgeOwner = YierLoomBridge.class.getClassLoader();
                        String helperLoader = helperOwner == null
                                ? "bootstrap" : helperOwner.getClass().getName();
                        String bridgeLoader = bridgeOwner == null
                                ? "bootstrap" : bridgeOwner.getClass().getName();
                        %s
                    }
                }
                """.formatted(packageName, extraImports, javaLiteral(id), dispatch);
    }

    private static String failingModuleSource(String packageName, String id) {
        return """
                package %s;

                import java.util.List;
                import com.nowcoder.yierloom.sdk.InstrumentationModule;
                import com.nowcoder.yierloom.sdk.TypeInstrumentation;

                public final class FixtureModule implements InstrumentationModule {
                    public String id() {
                        return %s;
                    }

                    public List<? extends TypeInstrumentation> typeInstrumentations() {
                        return List.of(new FixtureTypeInstrumentation());
                    }
                }
                """.formatted(packageName, javaLiteral(id + "-transformation"));
    }

    private static String failingTypeSource(String packageName) {
        return """
                package %s;

                import com.nowcoder.yierloom.sdk.TypeInstrumentation;
                import net.bytebuddy.agent.builder.AgentBuilder;
                import net.bytebuddy.description.type.TypeDescription;
                import net.bytebuddy.matcher.ElementMatcher;
                import static net.bytebuddy.matcher.ElementMatchers.named;

                public final class FixtureTypeInstrumentation implements TypeInstrumentation {
                    public ElementMatcher<? super TypeDescription> typeMatcher() {
                        return named("com.example.yierloom.integration.AgentTargetService");
                    }

                    public AgentBuilder.Transformer transformer() {
                        return (builder, type, loader, module, protectionDomain) -> {
                            throw new IllegalStateException("fixture transformation failure");
                        };
                    }
                }
                """.formatted(packageName);
    }

    private static String actionPrefix(String id) {
        return id.replace('-', '_');
    }

    private static Path prepareWorkspace(Path supplied) throws IOException {
        Path prepared = Objects.requireNonNull(supplied, "workspace")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(prepared);
        if (!Files.isDirectory(prepared) || !Files.isWritable(prepared)) {
            throw new IOException("plugin build workspace is not writable: " + prepared);
        }
        return prepared;
    }

    private static String javaLiteral(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (codePoint < 0x20 || codePoint > 0x7e) {
                        char[] units = Character.toChars(codePoint);
                        for (char unit : units) {
                            escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) unit));
                        }
                    } else {
                        escaped.append((char) codePoint);
                    }
                }
            }
        });
        return escaped.append('"').toString();
    }

    private enum RuntimeFixture {
        READY,
        START_FAILURE,
        STOP_FAILURE,
        INVALID_CONFIG
    }

    private enum HelperFixture {
        OBSERVE,
        EMIT_EVENT,
        WRITE_MARKER
    }
}
