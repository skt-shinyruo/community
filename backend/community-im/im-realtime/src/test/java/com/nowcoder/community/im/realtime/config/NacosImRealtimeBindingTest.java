package com.nowcoder.community.im.realtime.config;

import com.nowcoder.community.im.realtime.client.ImServiceClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NacosImRealtimeBindingTest {

    @Test
    void bindsImRealtimeSeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("im-realtime.yaml");
        ImServiceClientProperties properties = Binder.get(environment)
                .bind("im.clients", ImServiceClientProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(environment.containsProperty("im.clients.community-service-id")).isTrue();
        assertThat(environment.containsProperty("im.clients.im-core-service-id")).isTrue();
        assertThat(environment.containsProperty("im.clients.snapshot-timeout-ms")).isTrue();
        assertThat(properties.getCommunityServiceId()).isEqualTo("community-app");
        assertThat(properties.getImCoreServiceId()).isEqualTo("im-core");
        assertThat(properties.getSnapshotTimeoutMs()).isEqualTo(3000);
    }

    private static StandardEnvironment environmentFrom(String fileName) throws Exception {
        Path path = seedFile(fileName);
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new YamlPropertySourceLoader().load(fileName, new FileSystemResource(path)).get(0));
        return environment;
    }

    private static Path seedFile(String fileName) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("deploy/nacos/config").resolve(fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Nacos seed file not found: " + fileName);
    }
}
