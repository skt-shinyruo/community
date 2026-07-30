package com.nowcoder.yierloom.testkit.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.nowcoder.yierloom.api.YierLoomPlugin;

public final class PluginArchive implements AutoCloseable {
    private static final String SERVICE_ENTRY =
            "META-INF/services/" + YierLoomPlugin.class.getName();
    private static final List<String> FORBIDDEN_CLASS_PREFIXES = List.of(
            "com/nowcoder/yierloom/api/",
            "com/nowcoder/yierloom/sdk/",
            "com/nowcoder/yierloom/core/",
            "com/nowcoder/yierloom/bootstrap/",
            "com/nowcoder/yierloom/plugins/",
            "net/bytebuddy/");

    private final Path path;
    private final JarFile jar;
    private final List<String> entries;

    private PluginArchive(Path path, JarFile jar) {
        this.path = path;
        this.jar = jar;
        this.entries = jar.stream()
                .map(JarEntry::getName)
                .sorted()
                .toList();
    }

    public static PluginArchive open(Path suppliedPath) throws IOException {
        Objects.requireNonNull(suppliedPath, "pluginJar");
        Path path = suppliedPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException("plugin archive is not a readable file");
        }
        Path realPath = path.toRealPath();
        return new PluginArchive(
                realPath,
                new JarFile(realPath.toFile(), false, JarFile.OPEN_READ, Runtime.version()));
    }

    public Path path() {
        return path;
    }

    public List<String> entries() {
        return entries;
    }

    public List<String> forbiddenClassEntries() {
        return entries.stream()
                .filter(name -> name.endsWith(".class"))
                .filter(name -> FORBIDDEN_CLASS_PREFIXES.stream()
                        .anyMatch(normalizedClassEntry(name)::startsWith))
                .toList();
    }

    public List<String> providerDeclarations() throws IOException {
        JarEntry service = jar.getJarEntry(SERVICE_ENTRY);
        if (service == null || service.isDirectory()) {
            return List.of();
        }
        String content = new String(read(service), StandardCharsets.UTF_8);
        return content.lines()
                .map(line -> {
                    int comment = line.indexOf('#');
                    return (comment < 0 ? line : line.substring(0, comment)).trim();
                })
                .filter(line -> !line.isEmpty())
                .toList();
    }

    public byte[] classBytes(String binaryName) throws IOException {
        Objects.requireNonNull(binaryName, "binaryName");
        JarEntry entry = jar.getJarEntry(binaryName.replace('.', '/') + ".class");
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        return read(entry);
    }

    @Override
    public void close() throws IOException {
        jar.close();
    }

    private byte[] read(JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static String normalizedClassEntry(String name) {
        String versions = "META-INF/versions/";
        if (!name.startsWith(versions)) {
            return name;
        }
        int packageStart = name.indexOf('/', versions.length());
        return packageStart < 0 ? name : name.substring(packageStart + 1);
    }
}
