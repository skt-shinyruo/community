package com.nowcoder.community.platform.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构门禁：安全配置去漂移。
 *
 * <p>目标：</p>
 * <ul>
 *   <li>各服务 SecurityConfig 不再复制粘贴 JWT authorities converter 细节</li>
 *   <li>统一使用 infra-security-starter 提供的 {@code AuthoritiesConverterFactory}</li>
 * </ul>
 */
class PublicEndpointDriftGateTest {

    private static final Set<String> MODULES = Set.of(
            "auth-service",
            "user-service",
            "content-service",
            "social-service",
            "message-service",
            "search-service",
            "analytics-service",
            "ops-service",
            "gateway"
    );

    @Test
    void securityConfigsShouldUseSharedAuthoritiesConverterFactory() throws Exception {
        Path repoRoot = resolveRepoRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(p -> p.toString().endsWith("SecurityConfig.java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> isInManagedModules(repoRoot, p))
                    .forEach(p -> scanSecurityConfig(repoRoot, p, violations));
        }

        assertThat(violations)
                .as("Detected security config drift:\n" + String.join("\n", violations))
                .isEmpty();
    }

    private void scanSecurityConfig(Path repoRoot, Path javaFile, List<String> violations) {
        String content;
        try {
            content = Files.readString(javaFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            violations.add("[read-failed] file=" + repoRoot.relativize(javaFile) + " err=" + e.getMessage());
            return;
        }

        if (content.contains("getClaimAsStringList(\"authorities\")")
                || content.contains("new JwtAuthenticationConverter(")
                || content.contains("SimpleGrantedAuthority")) {
            violations.add("[jwt-converter-duplicated] file=" + repoRoot.relativize(javaFile));
            return;
        }

        if (!content.contains("AuthoritiesConverterFactory")) {
            violations.add("[jwt-converter-not-ssot] file=" + repoRoot.relativize(javaFile));
        }
    }

    private boolean isInManagedModules(Path repoRoot, Path javaFile) {
        Path rel;
        try {
            rel = repoRoot.relativize(javaFile);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (rel.getNameCount() < 2) {
            return false;
        }
        return MODULES.contains(rel.getName(0).toString());
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

