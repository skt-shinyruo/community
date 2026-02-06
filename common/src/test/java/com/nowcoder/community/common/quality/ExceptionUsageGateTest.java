package com.nowcoder.community.common.quality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产代码门禁：防止回潮到 catch(Exception)/throws Exception 这类“异常语义黑洞”写法。
 *
 * <p>约定：
 * <ul>
 *   <li>扫描范围：各模块 {@code src/main/java} 下的生产 Java 源码</li>
 *   <li>允许少量框架配置类保留 {@code throws Exception}（例如 Spring Security 配置）</li>
 * </ul>
 */
class ExceptionUsageGateTest {

    private static final Pattern CATCH_EXCEPTION = Pattern.compile("\\bcatch\\s*\\(\\s*Exception\\b");
    private static final Pattern THROWS_EXCEPTION = Pattern.compile("\\bthrows\\s+Exception\\b");

    @Test
    void srcMainShouldNotUseGenericExceptionInCatchOrThrows() throws IOException {
        Path repoRoot = resolveRepoRoot();
        List<Hit> hits = new ArrayList<>();

        for (Path srcMainJava : findSrcMainJavaDirs(repoRoot)) {
            try (Stream<Path> stream = Files.walk(srcMainJava)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .forEach(javaFile -> {
                            String content;
                            try {
                                content = Files.readString(javaFile, StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                throw new RuntimeException("read failed: " + javaFile, e);
                            }

                            Matcher catchMatcher = CATCH_EXCEPTION.matcher(content);
                            if (catchMatcher.find()) {
                                hits.add(new Hit(javaFile, lineOf(content, catchMatcher.start()), "catch(Exception)"));
                            }

                            if (!isThrowsExceptionAllowed(javaFile)) {
                                Matcher throwsMatcher = THROWS_EXCEPTION.matcher(content);
                                if (throwsMatcher.find()) {
                                    hits.add(new Hit(javaFile, lineOf(content, throwsMatcher.start()), "throws Exception"));
                                }
                            }
                        });
            }
        }

        assertThat(hits)
                .as(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("检测到生产代码使用泛化 Exception（请收敛到更具体异常或边界转换）：\n");
                    for (Hit h : hits) {
                        sb.append("- ").append(repoRoot.relativize(h.file()).toString())
                                .append(":").append(h.line())
                                .append(" (").append(h.kind()).append(")\n");
                    }
                    return sb.toString();
                })
                .isEmpty();
    }

    private boolean isThrowsExceptionAllowed(Path javaFile) {
        String name = javaFile == null ? "" : javaFile.getFileName().toString();
        return name.endsWith("SecurityConfig.java");
    }

    private List<Path> findSrcMainJavaDirs(Path repoRoot) throws IOException {
        if (repoRoot == null) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(repoRoot, 4)) {
            return stream.filter(Files::isDirectory)
                    .filter(p -> p.toString().endsWith("src/main/java"))
                    .toList();
        }
    }

    private Path resolveRepoRoot() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        Path p = cur;
        while (p != null) {
            if (Files.isDirectory(p.resolve("helloagents")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
            p = p.getParent();
        }
        return cur;
    }

    private int lineOf(String content, int index) {
        if (content == null || content.isEmpty() || index <= 0) {
            return 1;
        }
        int line = 1;
        for (int i = 0; i < Math.min(index, content.length()); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private record Hit(Path file, int line, String kind) {
    }
}
