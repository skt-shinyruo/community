package com.nowcoder.community.app.arch;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class BackendFlatteningArchTest {

    private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");

    @Test
    void backend_should_not_keep_legacy_split_modules() {
        Path backendRoot = detectBackendRoot();
        List<String> declaredModules = declaredModules(backendRoot.resolve("pom.xml"));

        assertThat(Files.isDirectory(backendRoot.resolve("community-im"))).isTrue();
        assertThat(Files.isDirectory(backendRoot.resolve("community-gateway"))).isTrue();
        assertThat(Files.isDirectory(backendRoot.resolve("community-app"))).isTrue();
        assertThat(declaredModules).containsExactly(
                "community-im",
                "community-gateway",
                "community-app"
        );
        assertThat(detectedMavenModules(backendRoot)).containsExactly(
                "community-app",
                "community-gateway",
                "community-im"
        );

        Path imRoot = backendRoot.resolve("community-im");
        List<String> imModules = declaredModules(imRoot.resolve("pom.xml"));
        assertThat(imModules).containsExactly(
                "im-common",
                "im-core",
                "im-realtime"
        );
        assertThat(Files.isDirectory(imRoot.resolve("im-common"))).isTrue();
        assertThat(Files.isDirectory(imRoot.resolve("im-core"))).isTrue();
        assertThat(Files.isDirectory(imRoot.resolve("im-realtime"))).isTrue();
        assertThat(detectedMavenModules(imRoot)).containsExactly(
                "im-common",
                "im-core",
                "im-realtime"
        );
    }

    private Path detectBackendRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("community-app"))) {
            return current;
        }
        if (current.getFileName() != null && "community-app".equals(current.getFileName().toString())) {
            return current.getParent();
        }
        throw new IllegalStateException("Unable to detect backend root from " + current);
    }

    private List<String> detectedMavenModules(Path root) {
        try (Stream<Path> paths = Files.list(root)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("pom.xml")))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to list Maven modules at " + root, e);
        }
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
