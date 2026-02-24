package com.nowcoder.community.gateway.arch;

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
 * 架构门禁：gateway（WebFlux）生产代码禁止依赖 ThreadLocal trace 上下文。
 *
 * <p>目的：</p>
 * <ul>
 *   <li>避免 reactive 链路里显式使用 TraceContext/TraceId（ThreadLocal/MDC），降低串线风险</li>
 *   <li>trace 透传应通过协议层（HTTP header / Dubbo attachment）完成</li>
 * </ul>
 */
class NoGatewayTraceContextUsageTest {

    private static final Pattern[] FORBIDDEN = new Pattern[]{
            Pattern.compile("\\bimport\\s+com\\.nowcoder\\.community\\.common\\.trace\\.TraceContext\\b"),
            Pattern.compile("\\bimport\\s+com\\.nowcoder\\.community\\.common\\.trace\\.TraceId\\b"),
            Pattern.compile("\\bcom\\.nowcoder\\.community\\.common\\.trace\\.TraceContext\\b"),
            Pattern.compile("\\bcom\\.nowcoder\\.community\\.common\\.trace\\.TraceId\\b")
    };

    @Test
    void gatewaySrcMainShouldNotUseTraceContextOrTraceIdThreadLocal() throws Exception {
        Path moduleRoot = findModuleRoot(Paths.get("").toAbsolutePath().normalize());
        Path dir = moduleRoot.resolve("src").resolve("main").resolve("java");
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("gateway/src/main/java 目录不存在：" + dir);
        }

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .forEach(p -> scanJavaFile(moduleRoot, p, violations));
        }

        assertThat(violations)
                .as("Detected forbidden TraceContext/TraceId usage in gateway src/main/java:\n" + String.join("\n", violations))
                .isEmpty();
    }

    private void scanJavaFile(Path moduleRoot, Path javaFile, List<String> violations) {
        String content;
        try {
            content = Files.readString(javaFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            violations.add("[read-failed] file=" + moduleRoot.relativize(javaFile) + " err=" + e.getMessage());
            return;
        }
        for (Pattern p : FORBIDDEN) {
            if (p.matcher(content).find()) {
                violations.add("[tracecontext-leak] file=" + moduleRoot.relativize(javaFile) + " pattern=" + p);
            }
        }
    }

    private Path findModuleRoot(Path start) {
        Path cur = start;
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve("pom.xml")) && Files.isDirectory(cur.resolve("src"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return start;
    }
}

