package com.nowcoder.yierloom.core.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.nowcoder.yierloom.api.YierLoomPlugin;

final class PluginJarFixture {
    private static final String SERVICE_ENTRY = "META-INF/services/" + YierLoomPlugin.class.getName();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private PluginJarFixture() {
    }

    static Path jarWithPrivateVersion(Path directory, String value) throws IOException {
        return jar(
                directory,
                "private-version.jar",
                Map.of("fixture.privatecopy.Version", """
                        package fixture.privatecopy;
                        public final class Version {
                            public static String value() { return \"%s\"; }
                        }
                        """.formatted(value)),
                null,
                List.of());
    }

    static Path jarWithPrivateCopies(Path directory) throws IOException {
        return jar(
                directory,
                "private-copies.jar",
                Map.of(
                        "fixture.privatecopy.One", """
                                package fixture.privatecopy;
                                public final class One { }
                                """,
                        "fixture.privatecopy.Two", """
                                package fixture.privatecopy;
                                public final class Two { }
                                """),
                null,
                List.of());
    }

    static Path providerJar(
            Path directory,
            String fileName,
            String className,
            String source
    ) throws IOException {
        return jar(directory, fileName, Map.of(className, source), List.of(className), List.of());
    }

    static Path jarWithoutProvider(Path directory, String fileName) throws IOException {
        return jar(directory, fileName, Map.of(), null, List.of());
    }

    static Path classesOnlyJar(
            Path directory,
            String fileName,
            String className,
            String source
    ) throws IOException {
        return jar(directory, fileName, Map.of(className, source), null, List.of());
    }

    static Path resourceOnlyJar(
            Path directory,
            String fileName,
            String resourceName,
            String value
    ) throws IOException {
        Files.createDirectories(directory);
        Path jar = directory.resolve(fileName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(output, resourceName, value.getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }

    static Path jarWithProviders(
            Path directory,
            String fileName,
            Map<String, String> sources,
            List<String> providers
    ) throws IOException {
        return jar(directory, fileName, sources, providers, List.of());
    }

    static Path jarWithForbiddenEntries(
            Path directory,
            String fileName,
            List<String> entries
    ) throws IOException {
        return jar(directory, fileName, Map.of(), null, entries);
    }

    static Path serviceOnlyJar(Path directory, String fileName, String providerName) throws IOException {
        return jar(directory, fileName, Map.of(), List.of(providerName), List.of());
    }

    static Path corruptJar(Path directory, String fileName) throws IOException {
        Files.createDirectories(directory);
        Path jar = directory.resolve(fileName);
        Files.writeString(jar, "not a jar", StandardCharsets.UTF_8);
        return jar;
    }

    private static Path jar(
            Path directory,
            String fileName,
            Map<String, String> sources,
            List<String> providers,
            List<String> additionalEntries
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
                    addEntry(output, name, Files.readAllBytes(file));
                }
            }
            for (String entry : additionalEntries.stream().sorted().toList()) {
                addEntry(output, entry, new byte[]{0});
            }
            if (providers != null) {
                String declaration = String.join("\n", providers) + (providers.isEmpty() ? "" : "\n");
                addEntry(output, SERVICE_ENTRY, declaration.getBytes(StandardCharsets.UTF_8));
            }
        }
        return jar;
    }

    private static void compile(List<Path> sourceFiles, Path classesDirectory) throws IOException {
        if (sourceFiles.isEmpty()) {
            return;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("tests require a JDK");
        }
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
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

    private static void addEntry(JarOutputStream output, String name, byte[] value) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(value);
        output.closeEntry();
    }
}
