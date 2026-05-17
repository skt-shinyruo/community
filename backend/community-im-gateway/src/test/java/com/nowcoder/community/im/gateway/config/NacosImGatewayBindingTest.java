package com.nowcoder.community.im.gateway.config;

import com.nowcoder.community.im.gateway.session.ImGatewaySessionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NacosImGatewayBindingTest {

    @Test
    void bindsImGatewaySeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("community-im-gateway.yaml");
        ImGatewaySessionProperties properties = Binder.get(environment)
                .bind("im.gateway", ImGatewaySessionProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(environment.containsProperty("im.gateway.worker.service-id")).isTrue();
        assertThat(environment.containsProperty("im.gateway.ws.path")).isTrue();
        assertThat(properties.getPublicWsUrl()).isEqualTo("ws://localhost:12880/ws/im");
        assertThat(properties.getWorker().getServiceId()).isEqualTo("im-realtime-worker");
        assertThat(properties.getWs().getPath()).isEqualTo("/ws/im");
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
