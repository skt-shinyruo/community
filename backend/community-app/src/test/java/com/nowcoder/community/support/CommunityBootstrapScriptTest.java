package com.nowcoder.community.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityBootstrapScriptTest {

    private static final Path MODULE_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path REPO_ROOT = MODULE_ROOT.getParent().getParent();
    private static final Path COMMUNITY_BOOTSTRAP_DIR = REPO_ROOT.resolve("deploy/mysql/community");

    @Test
    void bootstrapScriptShouldApplyEveryCommunitySqlFileInFilenameOrder() throws IOException {
        List<String> deploySqlFiles;
        try (var paths = Files.list(COMMUNITY_BOOTSTRAP_DIR)) {
            deploySqlFiles = paths
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(".sql"))
                    .sorted()
                    .toList();
        }

        assertThat(readBootstrapSchemaFiles()).containsExactlyElementsOf(deploySqlFiles);
    }

    private List<String> readBootstrapSchemaFiles() throws IOException {
        String script = Files.readString(COMMUNITY_BOOTSTRAP_DIR.resolve("001_bootstrap.sh"));
        var matcher = Pattern.compile("SCHEMA_FILES=\\((?<files>.*?)\\)", Pattern.DOTALL).matcher(script);

        assertThat(matcher.find()).as("SCHEMA_FILES array should exist").isTrue();

        return matcher.group("files")
                .lines()
                .map(String::trim)
                .filter(line -> line.endsWith(".sql"))
                .toList();
    }
}
