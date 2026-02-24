package com.nowcoder.community.common.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构门禁：Outbox-only 默认安全态。
 *
 * <p>允许存在“事务提交后直发”代码，但必须被显式应急开关保护（direct-send-enabled）。</p>
 */
class OutboxOnlyGateTest {

    @Test
    void kafkaAfterCommitDirectSendMustBeGuardedByEmergencySwitch() throws Exception {
        Path repoRoot = resolveRepoRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .forEach(p -> scanJavaFile(repoRoot, p, violations));
        }

        assertThat(violations)
                .as("Detected unguarded direct-send usages (AfterCommit + Kafka):\n" + String.join("\n", violations))
                .isEmpty();
    }

    private void scanJavaFile(Path repoRoot, Path javaFile, List<String> violations) {
        String content;
        try {
            content = Files.readString(javaFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            violations.add("[read-failed] file=" + repoRoot.relativize(javaFile) + " err=" + e.getMessage());
            return;
        }

        if (!content.contains("AfterCommitExecutor.runAfterCommit")) {
            return;
        }

        boolean looksLikeKafkaPublisher = content.contains("kafkaTemplate.send")
                || content.contains("KafkaTemplate<")
                || content.contains("KafkaTemplate ");
        if (!looksLikeKafkaPublisher) {
            return;
        }

        if (!content.contains("isDirectSendEnabled()")) {
            violations.add("[direct-send-unguarded] file=" + repoRoot.relativize(javaFile));
        }
    }

    private Path resolveRepoRoot() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        Path p = cur;
        while (p != null) {
            if (Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
            p = p.getParent();
        }
        return cur;
    }
}

