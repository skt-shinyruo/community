package com.nowcoder.community.bootstrap.arch;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoDistributedProjectionInfraArchTest {

    private static final List<String> FORBIDDEN_MAIN_PATHS = List.of(
            "community-bootstrap/src/main/java/com/nowcoder/community/infra/outbox",
            "community-bootstrap/src/main/java/com/nowcoder/community/infra/kafka",
            "community-bootstrap/src/main/java/com/nowcoder/community/content/api/rpc/ContentOutboxRpcService.java",
            "community-bootstrap/src/main/java/com/nowcoder/community/content/rpc/ContentOutboxRpcServiceImpl.java",
            "community-bootstrap/src/main/java/com/nowcoder/community/social/api/rpc/SocialOutboxRpcService.java",
            "community-bootstrap/src/main/java/com/nowcoder/community/social/rpc/SocialOutboxRpcServiceImpl.java",
            "community-bootstrap/src/main/java/com/nowcoder/community/user/api/rpc/UserOutboxRpcService.java",
            "community-bootstrap/src/main/java/com/nowcoder/community/user/rpc/UserOutboxRpcServiceImpl.java"
    );

    @Test
    void backend_should_not_keep_runtime_kafka_or_outbox_projection_infrastructure() {
        Path backendRoot = detectBackendRoot();
        for (String relative : FORBIDDEN_MAIN_PATHS) {
            assertThat(backendRoot.resolve(relative))
                    .as("distributed projection artifact should be removed: %s", relative)
                    .doesNotExist();
        }
    }

    private Path detectBackendRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("community-bootstrap"))) {
            return current;
        }
        if (current.getFileName() != null && "community-bootstrap".equals(current.getFileName().toString())) {
            return current.getParent();
        }
        throw new IllegalStateException("Unable to detect backend root from " + current);
    }
}
