package com.nowcoder.community.bootstrap.arch;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class BackendFlatteningArchTest {

    private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");

    private static final List<String> LEGACY_MODULES = List.of(
            "auth-service",
            "user-service",
            "content-service",
            "social-service",
            "message-service",
            "search-service",
            "analytics-service",
            "ops-service",
            "platform"
    );

    @Test
    void backend_should_not_keep_legacy_split_modules() {
        Path backendRoot = detectBackendRoot();
        List<String> declaredModules = declaredModules(backendRoot.resolve("pom.xml"));

        assertThat(Files.isDirectory(backendRoot.resolve("community-bootstrap"))).isTrue();
        assertThat(declaredModules).containsExactly(
                "im-contracts",
                "im-core",
                "im-realtime",
                "community-bootstrap"
        );
        for (String module : LEGACY_MODULES) {
            assertThat(backendRoot.resolve(module))
                    .as("legacy module should be removed: %s", module)
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

    private List<String> declaredModules(Path pomFile) {
        try {
            String xml = Files.readString(pomFile);
            Matcher matcher = MODULE_PATTERN.matcher(xml);
            java.util.ArrayList<String> modules = new java.util.ArrayList<>();
            while (matcher.find()) {
                modules.add(matcher.group(1).trim());
            }
            return modules;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read backend pom at " + pomFile, e);
        }
    }
}
