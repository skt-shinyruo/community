package com.nowcoder.community.im.common.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImCommonLayoutTest {

    @Test
    void legacy_contracts_test_package_should_not_remain() throws IOException {
        assertTrue(
                sourceFilesUnder("src/test/java/com/nowcoder/community/im/contracts").isEmpty(),
                "im-common test sources should not keep the legacy im/contracts path"
        );
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
