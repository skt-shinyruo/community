package com.nowcoder.community.common.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构门禁（轻量版）：
 * - 禁止跨域 import 域错误码枚举（ErrorCode ownership）
 * - 禁止 infra/contracts/common/gateway/ops 等模块直接依赖 domain event payload（避免“共享内核式耦合”回潮）
 *
 * <p>说明：此测试不依赖额外框架（ArchUnit），以源码扫描形式实现，便于在多模块仓库中快速落地。</p>
 */
class NoCrossDomainContractImportTest {

    private static final Map<String, Set<String>> ERROR_CODE_ALLOWED_MODULES = new HashMap<>();

    static {
        allow("com.nowcoder.community.content.api.ContentErrorCode", Set.of("content-api", "content-service"));
        allow("com.nowcoder.community.user.api.UserErrorCode", Set.of("user-api", "user-service"));
        allow("com.nowcoder.community.social.api.SocialErrorCode", Set.of("social-api", "social-service"));
        allow("com.nowcoder.community.search.api.SearchErrorCode", Set.of("search-api", "search-service"));
        allow("com.nowcoder.community.analytics.api.AnalyticsErrorCode", Set.of("analytics-api", "analytics-service"));

        // 没有独立 *-api 的域：枚举只允许在服务内部使用
        allow("com.nowcoder.community.auth.api.AuthErrorCode", Set.of("auth-service"));
        allow("com.nowcoder.community.message.api.MessageErrorCode", Set.of("message-service"));
        allow("com.nowcoder.community.gateway.api.GatewayErrorCode", Set.of("gateway"));
    }

    private static void allow(String fqcn, Set<String> modules) {
        ERROR_CODE_ALLOWED_MODULES.put(fqcn, new HashSet<>(modules));
    }

    @Test
    void shouldNotImportOtherDomainErrorCodes_orLeakPayloadIntoInfraModules() throws Exception {
        Path repoRoot = findRepoRoot(Paths.get("").toAbsolutePath());
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .forEach(p -> scanJavaFile(repoRoot, p, violations));
        }

        assertThat(violations)
                .as("Detected forbidden cross-module imports:\n" + String.join("\n", violations))
                .isEmpty();
    }

    private void scanJavaFile(Path repoRoot, Path javaFile, List<String> violations) {
        String module = moduleName(repoRoot, javaFile);
        if (module == null || module.isBlank()) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            violations.add("[read-failed] " + repoRoot.relativize(javaFile) + ": " + e.getMessage());
            return;
        }

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("import ")) {
                continue;
            }
            String imported = trimmed
                    .replace("import static ", "")
                    .replace("import ", "")
                    .replace(";", "")
                    .trim();

            // 1) 域错误码跨域 import 门禁
            for (Map.Entry<String, Set<String>> e : ERROR_CODE_ALLOWED_MODULES.entrySet()) {
                String fqcn = e.getKey();
                if (imported.equals(fqcn) || imported.startsWith(fqcn + ".")) {
                    if (!e.getValue().contains(module)) {
                        violations.add("[errorcode-cross-domain] module=" + module
                                + " file=" + repoRoot.relativize(javaFile)
                                + " import=" + imported);
                    }
                }
            }

            // 2) infra/contracts/common/gateway/ops 不应依赖任何 domain payload
            if (isInfraLikeModule(module) && imported.contains(".api.event.payload.")) {
                violations.add("[payload-leak-into-infra] module=" + module
                        + " file=" + repoRoot.relativize(javaFile)
                        + " import=" + imported);
            }
        }
    }

    private boolean isInfraLikeModule(String module) {
        if (module == null) {
            return false;
        }
        if (module.startsWith("infra-")) {
            return true;
        }
        return module.equals("common")
                || module.equals("contracts-core")
                || module.equals("contracts-event-core")
                || module.equals("gateway")
                || module.equals("ops-service");
    }

    private String moduleName(Path repoRoot, Path javaFile) {
        Path rel;
        try {
            rel = repoRoot.relativize(javaFile);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (rel.getNameCount() < 2) {
            return null;
        }
        return rel.getName(0).toString();
    }

    private Path findRepoRoot(Path start) {
        Path p = start;
        while (p != null) {
            Path pom = p.resolve("pom.xml");
            if (Files.exists(pom)) {
                try {
                    String s = Files.readString(pom, StandardCharsets.UTF_8);
                    // 根 pom 才会包含 <modules>，子模块 pom 通常不包含
                    if (s.contains("<modules>") && s.contains("<module>contracts-core</module>")) {
                        return p;
                    }
                } catch (IOException ignored) {
                }
            }
            p = p.getParent();
        }
        return start;
    }
}

