package com.nowcoder.yierloom.bootstrap;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NestedJarExtractorTest {
    private static final List<String> REQUIRED_ENTRIES = List.of(
            "META-INF/yierloom/lib/yierloom-plugin-api.jar",
            "META-INF/yierloom/lib/yierloom-bytebuddy-sdk.jar",
            "META-INF/yierloom/lib/yierloom-agent-core.jar",
            "META-INF/yierloom/lib/byte-buddy.jar");

    @TempDir
    Path tempDir;

    @Test
    void extractsOnlyTheFixedNestedLibrariesUnderAPrivateDirectory() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        REQUIRED_ENTRIES.forEach(entry -> contents.put(entry, entry));
        contents.put("META-INF/yierloom/lib/ignored.jar", "ignored");
        contents.put("../escape.jar", "escape");
        Path outerJar = jar("outer.jar", contents);
        Path extractionParent = Files.createDirectories(tempDir.resolve("extractions"));

        Path extracted = NestedJarExtractor.extract(outerJar, extractionParent);

        assertThat(extracted.getParent()).isEqualTo(extractionParent.toAbsolutePath().normalize());
        assertThat(extracted.getFileName().toString()).startsWith("yierloom-");
        for (String entry : REQUIRED_ENTRIES) {
            Path file = extracted.resolve(entry);
            assertThat(file).isRegularFile();
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(entry);
        }
        assertThat(extracted.resolve("META-INF/yierloom/lib/ignored.jar")).doesNotExist();
        assertThat(extractionParent.resolve("escape.jar")).doesNotExist();
    }

    @Test
    void missingRequiredEntryRemovesThePartialExtraction() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        REQUIRED_ENTRIES.subList(0, REQUIRED_ENTRIES.size() - 1)
                .forEach(entry -> contents.put(entry, entry));
        Path outerJar = jar("incomplete.jar", contents);
        Path extractionParent = Files.createDirectories(tempDir.resolve("extractions"));

        assertThatThrownBy(() -> NestedJarExtractor.extract(outerJar, extractionParent))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("nested library");

        try (var children = Files.list(extractionParent)) {
            assertThat(children).isEmpty();
        }
    }

    @Test
    void cleanupRefusesAReplacementAtThePrivateDirectoryPath() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        REQUIRED_ENTRIES.forEach(entry -> contents.put(entry, entry));
        Path outerJar = jar("outer.jar", contents);
        Path extractionParent = Files.createDirectories(tempDir.resolve("extractions"));
        NestedJarExtractor.Extraction extraction = NestedJarExtractor.extractOwned(
                outerJar, extractionParent);
        Path original = extractionParent.resolve("original-extraction");
        Files.move(extraction.directory(), original);
        Path replacement = Files.createDirectories(extraction.directory());
        Path foreign = Files.writeString(replacement.resolve("foreign.txt"), "keep");

        assertThatThrownBy(() -> NestedJarExtractor.deletePrivateDirectory(extraction))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cleanup failed");

        assertThat(foreign).exists();
        assertThat(original.resolve("META-INF/yierloom/lib/yierloom-plugin-api.jar")).exists();
    }

    @Test
    void cleanupDeletesTheOwnedDirectoryAndIsIdempotent() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        REQUIRED_ENTRIES.forEach(entry -> contents.put(entry, entry));
        Path outerJar = jar("owned.jar", contents);
        NestedJarExtractor.Extraction extraction = NestedJarExtractor.extractOwned(
                outerJar, Files.createDirectories(tempDir.resolve("owned-extractions")));

        NestedJarExtractor.deletePrivateDirectory(extraction);
        NestedJarExtractor.deletePrivateDirectory(extraction);

        assertThat(extraction.directory()).doesNotExist();
    }

    @Test
    void cleanupDoesNotTraverseAReplacementSymlinkInsideTheOwnedDirectory() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        REQUIRED_ENTRIES.forEach(entry -> contents.put(entry, entry));
        Path outerJar = jar("symlink.jar", contents);
        NestedJarExtractor.Extraction extraction = NestedJarExtractor.extractOwned(
                outerJar, Files.createDirectories(tempDir.resolve("symlink-extractions")));
        Path root = extraction.directory();
        Files.move(root.resolve("META-INF"), root.resolve("owned-meta"));
        Path foreignLibrary = Files.createDirectories(
                        tempDir.resolve("foreign").resolve("yierloom").resolve("lib"))
                .resolve("yierloom-plugin-api.jar");
        Files.writeString(foreignLibrary, "keep");
        Files.createSymbolicLink(root.resolve("META-INF"), tempDir.resolve("foreign"));

        NestedJarExtractor.deletePrivateDirectory(extraction);

        assertThat(foreignLibrary).hasContent("keep");
        assertThat(root).doesNotExist();
    }

    @Test
    void ownerTokenSupportsCleanupWhenTheFileSystemHasNoFileKeys() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        REQUIRED_ENTRIES.forEach(entry -> contents.put(entry, entry));
        NestedJarExtractor.Extraction extracted = NestedJarExtractor.extractOwned(
                jar("null-key.jar", contents),
                Files.createDirectories(tempDir.resolve("null-key-extractions")));
        NestedJarExtractor.Extraction withoutKeys = new NestedJarExtractor.Extraction(
                extracted.directory(),
                null,
                new AtomicReference<>(),
                extracted.token(),
                new AtomicBoolean());

        NestedJarExtractor.deletePrivateDirectory(withoutKeys);

        assertThat(extracted.directory()).doesNotExist();
    }

    private Path jar(String name, Map<String, String> entries) throws IOException {
        Path jar = tempDir.resolve(name);
        try (OutputStream output = Files.newOutputStream(jar);
             JarOutputStream archive = new JarOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                archive.putNextEntry(new JarEntry(entry.getKey()));
                archive.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        }
        return jar;
    }
}
