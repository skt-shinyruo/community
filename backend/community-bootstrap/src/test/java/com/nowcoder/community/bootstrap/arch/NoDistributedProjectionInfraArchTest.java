package com.nowcoder.community.bootstrap.arch;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NoDistributedProjectionInfraArchTest {

    private static final String FORBIDDEN_DIR_SEGMENT = String.valueOf(new char[]{'r', 'p', 'c'});

    @Test
    void backend_should_not_keep_runtime_kafka_or_distributed_outbox_projection_infrastructure() {
        Path backendRoot = detectBackendRoot();

        Path mainRoot = backendRoot.resolve("community-bootstrap/src/main/java");
        assertThat(backendRoot.resolve("community-bootstrap/src/main/java/com/nowcoder/community/infra/kafka"))
                .as("runtime Kafka infrastructure should not exist in the flattened monolith")
                .doesNotExist();

        assertThat(containsPathSegment(mainRoot, FORBIDDEN_DIR_SEGMENT))
                .as("main sources should not contain forbidden directory segment: %s", FORBIDDEN_DIR_SEGMENT)
                .isFalse();
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

    private boolean containsPathSegment(Path root, String segment) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.anyMatch(path -> {
                for (Path part : path) {
                    if (part.getFileName() != null && part.getFileName().toString().equals(segment)) {
                        return true;
                    }
                }
                return false;
            });
        } catch (Exception e) {
            throw new IllegalStateException("Unable to scan sources at " + root, e);
        }
    }
}
