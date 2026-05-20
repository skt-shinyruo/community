package com.nowcoder.community.app.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionJdbcPersistenceGuardTest {

    @Test
    void productionPersistenceRepositoriesShouldNotUseJdbcTemplateDirectly() throws IOException {
        Path sourceRoot = Path.of("src/main/java/com/nowcoder/community").toAbsolutePath().normalize();
        List<String> violations;
        try (var files = Files.walk(sourceRoot)) {
            violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("Repository.java"))
                    .filter(path -> path.toString().contains("/infrastructure/persistence/"))
                    .filter(path -> importsJdbcTemplate(path) || declaresJdbcRepository(path))
                    .map(sourceRoot::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertThat(violations)
                .as("production persistence repositories should use MyBatis mappers, not JdbcTemplate")
                .isEmpty();
    }

    private static boolean importsJdbcTemplate(Path path) {
        return read(path).contains("org.springframework.jdbc.core.JdbcTemplate");
    }

    private static boolean declaresJdbcRepository(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.startsWith("Jdbc")) {
            return false;
        }
        return read(path).contains("@Repository");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
