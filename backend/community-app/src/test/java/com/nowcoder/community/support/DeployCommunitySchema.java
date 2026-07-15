package com.nowcoder.community.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DeployCommunitySchema {

    private DeployCommunitySchema() {
    }

    public static String read(Path repoRoot) throws IOException {
        return Files.readString(repoRoot.resolve(
                "backend/community-db-migrations/src/main/resources/db/migration/community/V001__baseline.sql"));
    }
}
