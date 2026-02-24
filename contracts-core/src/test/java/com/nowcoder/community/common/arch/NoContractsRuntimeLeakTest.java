package com.nowcoder.community.common.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构门禁：contracts-core 只能承载“稳定协议”，禁止引入运行期实现细节。
 *
 * <p>禁止项示例：</p>
 * <ul>
 *   <li>ThreadLocal/MDC 等线程上下文实现</li>
 *   <li>Spring Web / Servlet / Reactor 等运行期框架依赖</li>
 * </ul>
 */
class NoContractsRuntimeLeakTest {

    private static final Pattern[] FORBIDDEN = new Pattern[]{
            Pattern.compile("\\bThreadLocal\\s*<"),
            Pattern.compile("\\bnew\\s+ThreadLocal\\s*<"),
            Pattern.compile("\\bimport\\s+org\\.slf4j\\.MDC\\b"),
            Pattern.compile("\\bimport\\s+jakarta\\.servlet\\b"),
            Pattern.compile("\\bimport\\s+org\\.springframework\\.(web|security|boot)\\b"),
            Pattern.compile("\\bimport\\s+reactor\\.core\\b")
    };

    @Test
    void contractsCoreShouldNotContainRuntimeImplementations() throws Exception {
        Path repoRoot = findRepoRoot(Paths.get("").toAbsolutePath());
        Path dir = repoRoot.resolve("contracts-core").resolve("src").resolve("main").resolve("java");
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("contracts-core/src/main/java 目录不存在：" + dir);
        }

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> scanJavaFile(repoRoot, p, violations));
        }

        assertThat(violations)
                .as("Detected runtime-leak in contracts-core:\n" + String.join("\n", violations))
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
        for (Pattern p : FORBIDDEN) {
            if (p.matcher(content).find()) {
                violations.add("[runtime-leak] file=" + repoRoot.relativize(javaFile) + " pattern=" + p);
            }
        }
    }

    private Path findRepoRoot(Path start) {
        Path cur = start;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("helloagents")) && Files.isRegularFile(cur.resolve("pom.xml"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return start;
    }
}
