package com.nowcoder.community.platform.quality;

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

class UnitTestOnlyGateTest {

    // 注意：此处禁止出现完整 token 字面量，否则本测试会“自我命中”导致误报。
    private static final String SPRING_BOOT_TEST = "@Spring" + "BootTest";
    private static final String WEB_MVC_TEST = "@Web" + "MvcTest";
    private static final String WEB_FLUX_TEST = "@Web" + "FluxTest";
    private static final String DATA_JPA_TEST = "@Data" + "JpaTest";
    private static final String JDBC_TEST = "@Jdbc" + "Test";
    private static final String AUTO_CONFIG_MOCKMVC = "@AutoConfigure" + "MockMvc";
    private static final String AUTO_CONFIG_WEBTESTCLIENT = "@AutoConfigure" + "WebTestClient";
    private static final String TESTCONTAINERS = "Test" + "containers";
    private static final String GENERIC_CONTAINER = "Generic" + "Container";
    private static final String DOCKER_IMAGE_NAME = "Docker" + "ImageName";
    private static final String REACTOR_HTTP_SERVER = "reactor.netty.http.server." + "HttpServer";
    private static final String BIND_NOW = ".bind" + "Now(";

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            SPRING_BOOT_TEST,
            WEB_MVC_TEST,
            WEB_FLUX_TEST,
            DATA_JPA_TEST,
            JDBC_TEST,
            AUTO_CONFIG_MOCKMVC,
            AUTO_CONFIG_WEBTESTCLIENT,
            TESTCONTAINERS,
            GENERIC_CONTAINER,
            DOCKER_IMAGE_NAME,
            REACTOR_HTTP_SERVER,
            BIND_NOW
    );

    @Test
    void shouldNotContainIntegrationTestPatternsInRepository() throws IOException {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path repoRoot = moduleDir.getParent();
        if (repoRoot == null) {
            repoRoot = moduleDir;
        }

        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/test/java/"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .forEach(p -> checkFile(p, violations));
        }

        assertThat(violations)
                .as("仓库已约定只保留单元测试：禁止 " + SPRING_BOOT_TEST + "/" + TESTCONTAINERS + "/本地 " + REACTOR_HTTP_SERVER + " 等集成测试形态")
                .isEmpty();
    }

    private static void checkFile(Path path, List<String> violations) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            violations.add(path + "\tREAD_ERROR:" + e.getClass().getSimpleName());
            return;
        }
        for (String token : FORBIDDEN_TOKENS) {
            if (content.contains(token)) {
                violations.add(path + "\t" + token);
            }
        }
    }
}
