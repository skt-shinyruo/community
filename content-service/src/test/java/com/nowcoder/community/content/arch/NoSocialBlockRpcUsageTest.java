package com.nowcoder.community.content.arch;

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
 * 架构门禁：content-service 生产代码禁止同步依赖 social-service 的“拉黑关系查询”点查 RPC。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>写路径的反骚扰校验应依赖本地投影（最终一致）</li>
 *   <li>冷启动/补洞通过 SocialBlockScanRpcService（scan）做 bootstrap，而非 per-request 点查</li>
 * </ul>
 */
class NoSocialBlockRpcUsageTest {

    private static final Pattern[] FORBIDDEN = new Pattern[]{
            Pattern.compile("\\bimport\\s+com\\.nowcoder\\.community\\.social\\.api\\.rpc\\.SocialBlockRpcService\\b"),
            Pattern.compile("\\bcom\\.nowcoder\\.community\\.social\\.api\\.rpc\\.SocialBlockRpcService\\b")
    };

    @Test
    void contentServiceSrcMainShouldNotUseSocialBlockRpcService() throws Exception {
        Path moduleRoot = findModuleRoot(Paths.get("").toAbsolutePath().normalize());
        Path dir = moduleRoot.resolve("src").resolve("main").resolve("java");
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("content-service/src/main/java 目录不存在：" + dir);
        }

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .forEach(p -> scanJavaFile(moduleRoot, p, violations));
        }

        assertThat(violations)
                .as("Detected forbidden SocialBlockRpcService usage in content-service src/main/java:\n" + String.join("\n", violations))
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
                violations.add("[social-block-rpc-leak] file=" + moduleRoot.relativize(javaFile) + " pattern=" + p);
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

