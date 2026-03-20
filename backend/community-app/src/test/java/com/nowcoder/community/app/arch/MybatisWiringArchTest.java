package com.nowcoder.community.app.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardrails for MyBatis wiring in the flattened modular monolith.
 *
 * <p>Composition root owns infrastructure wiring. Domain packages should not carry duplicated wiring that can drift.</p>
 */
class MybatisWiringArchTest {

    @Test
    void bootstrap_should_not_use_type_aliases_package_and_should_have_single_mapper_scan() throws IOException {
        Path backendRoot = detectBackendRoot();

        assertThat(readFile(backendRoot.resolve("community-app/src/main/resources/application.yml")))
                .doesNotContain("type-aliases-package:");
        assertThat(readFile(backendRoot.resolve("community-app/src/test/resources/application.yml")))
                .doesNotContain("type-aliases-package:");

        int mapperScanCount = countOccurrences(
                backendRoot.resolve("community-app/src/main/java"),
                "@MapperScan"
        );
        assertThat(mapperScanCount)
                .as("MyBatis mapper scanning should be centralized (exactly one @MapperScan in main sources)")
                .isEqualTo(1);
    }

    private static Path detectBackendRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("community-app"))) {
            return current;
        }
        if (current.getFileName() != null && "community-app".equals(current.getFileName().toString())) {
            return current.getParent();
        }
        throw new IllegalStateException("Unable to detect backend root from " + current);
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read file: " + file, e);
        }
    }

    private static int countOccurrences(Path root, String needle) throws IOException {
        AtomicInteger count = new AtomicInteger();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            int idx = 0;
                            while ((idx = content.indexOf(needle, idx)) >= 0) {
                                count.incrementAndGet();
                                idx += needle.length();
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Unable to read java source: " + p, e);
                        }
                    });
        }
        return count.get();
    }
}
