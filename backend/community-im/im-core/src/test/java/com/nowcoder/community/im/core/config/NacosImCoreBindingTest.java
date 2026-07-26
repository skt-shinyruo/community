package com.nowcoder.community.im.core.config;

import com.nowcoder.community.im.core.security.ImCoreCorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NacosImCoreBindingTest {

    @Test
    void bindsImCoreKafkaConsumerSeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("im-core.yaml");

        assertThat(environment.getProperty("spring.kafka.consumer.group-id")).isEqualTo("im-core");
        assertThat(environment.getProperty("spring.kafka.consumer.auto-offset-reset")).isEqualTo("earliest");
        assertThat(environment.containsProperty("im.kafka.consumer.group-id")).isFalse();
        assertThat(environment.containsProperty("im.kafka.consumer.auto-offset-reset")).isFalse();
        assertThat(environment.getProperty("im.cors.allowed-origins[2]")).isEqualTo("http://localhost:12881");

        ImCoreCorsProperties properties = Binder.get(environment)
                .bind("im.cors", Bindable.of(ImCoreCorsProperties.class))
                .orElseThrow(() -> new AssertionError("im.cors properties did not bind"));
        assertThat(properties.getAllowedOrigins())
                .containsExactly(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:12881",
                        "http://127.0.0.1:12881",
                        "http://localhost:12888",
                        "http://127.0.0.1:12888"
                );
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
