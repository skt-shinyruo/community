package com.nowcoder.community.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DeployCommunitySchema {

    private DeployCommunitySchema() {
    }

    public static String read(Path repoRoot) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (var paths = Files.list(repoRoot.resolve("deploy/mysql/community"))) {
            for (Path path : paths
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                builder.append(Files.readString(path)).append('\n');
            }
        }
        return builder.toString();
    }
}
