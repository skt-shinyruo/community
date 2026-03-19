package com.nowcoder.community.im.core.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ImCoreLayerNamingTest {

    @Test
    void api_package_should_not_remain_in_main_sources() throws IOException {
        assertThat(sourceFilesUnder("src/main/java/com/nowcoder/community/im/core/api"))
                .as("im-core main sources should not keep the legacy api package")
                .isEmpty();
    }

    @Test
    void db_package_should_not_remain_in_main_sources() throws IOException {
        assertThat(sourceFilesUnder("src/main/java/com/nowcoder/community/im/core/db"))
                .as("im-core main sources should not keep the legacy db package")
                .isEmpty();
    }

    private List<String> sourceFilesUnder(String relativePath) throws IOException {
        Path root = Paths.get("").toAbsolutePath().normalize();
        Path dir = root.resolve(relativePath);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString())
                    .sorted()
                    .toList();
        }
    }
}
