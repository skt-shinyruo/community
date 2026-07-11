package com.nowcoder.community.im.realtime.config;

import com.nowcoder.community.im.realtime.client.ImServiceClientProperties;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NacosImRealtimeBindingTest {

    @Test
    void bindsImRealtimeSeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("im-realtime.yaml");
        ImServiceClientProperties properties = Binder.get(environment)
                .bind("im.clients", ImServiceClientProperties.class)
                .orElseThrow(IllegalStateException::new);
        ImSessionProperties sessionProperties = Binder.get(environment)
                .bind("im.session", ImSessionProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(environment.containsProperty("im.clients.community-service-id")).isTrue();
        assertThat(environment.containsProperty("im.clients.im-core-service-id")).isTrue();
        assertThat(environment.containsProperty("im.clients.snapshot-timeout-ms")).isTrue();
        assertThat(environment.containsProperty("im.community.timeout-ms")).isTrue();
        assertThat(environment.containsProperty("im.projection.bootstrap-on-startup")).isTrue();
        assertThat(environment.containsProperty("im.room-presence.enabled")).isTrue();
        assertThat(environment.containsProperty("im.room-fanout.mode")).isFalse();
        assertThat(environment.containsProperty("im.room-fanout.transport")).isFalse();
        assertThat(environment.containsProperty("im.room-fanout.owner-flush-interval")).isFalse();
        assertThat(environment.containsProperty("im.room-fanout.target-path")).isFalse();
        assertThat(environment.containsProperty("im.room-fanout.target-timeout")).isFalse();
        assertThat(environment.containsProperty("im.room-fanout.owner-group-id")).isTrue();
        assertThat(environment.containsProperty("im.room-fanout.routed-command-topic")).isTrue();
        assertThat(environment.containsProperty("im.room-fanout.publish-timeout")).isTrue();
        assertThat(environment.containsProperty("im.kafka.event.concurrency")).isTrue();
        assertThat(environment.containsProperty("im.ws.room-flush-interval-ms")).isTrue();
        assertThat(environment.containsProperty("im.ws.max-inbound-chars")).isTrue();
        assertThat(properties.getCommunityServiceId()).isEqualTo("community-app");
        assertThat(properties.getImCoreServiceId()).isEqualTo("im-core");
        assertThat(properties.getSnapshotTimeoutMs()).isEqualTo(3000);
        assertThat(environment.getProperty("im.community.timeout-ms", Integer.class)).isEqualTo(1500);
        assertThat(environment.getProperty("im.ws.max-inbound-chars", Integer.class)).isEqualTo(10000);
        assertThat(environment.getProperty("im.ws.room-flush-interval-ms", Integer.class)).isEqualTo(50);
        assertThat(environment.getProperty("im.room-presence.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("im.room-presence.ttl")).isEqualTo("PT30S");
        assertThat(environment.getProperty("im.room-fanout.owner-group-id")).isEqualTo("im-realtime-room-fanout-owner");
        assertThat(environment.getProperty("im.room-fanout.routed-command-topic")).isEqualTo("im.command.room-fanout-routed");
        assertThat(environment.getProperty("im.room-fanout.routed-command-partitions", Integer.class)).isEqualTo(64);
        assertThat(rawProperty(environment, "im-realtime.yaml", "im.room-fanout.routed-command-partitions"))
                .isEqualTo(64);
        assertThat(rawProperty(environment, "im-realtime.yaml", "im.room-fanout.worker-inbox-slot"))
                .isEqualTo("${IM_ROOM_FANOUT_WORKER_INBOX_SLOT}");
        assertThat(environment.getProperty("im.room-fanout.worker-inbox-slot", Integer.class)).isZero();
        assertThat(environment.getProperty("im.room-fanout.publish-timeout")).isEqualTo("PT1S");
        assertThat(environment.getProperty("im.cors.allowed-origins[2]")).isEqualTo("http://localhost:12881");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.metadata.draining", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.metadata.maxConnections", Integer.class)).isEqualTo(10000);
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.metadata.activeConnectionHint", Integer.class)).isZero();
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.metadata.shardGroup")).isEqualTo("default");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.metadata.capacityWeight", Integer.class)).isEqualTo(100);
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.metadata.roomFanoutInboxSlot", Integer.class))
                .isZero();
        assertThat(rawProperty(environment, "im-realtime.yaml", "spring.cloud.nacos.discovery.metadata.roomFanoutInboxSlot"))
                .isEqualTo("${im.room-fanout.worker-inbox-slot}");
        assertThat(rawProperty(environment, "im-realtime.yaml", "spring.kafka.consumer.group-id"))
                .isEqualTo("im-realtime-${IM_REALTIME_WORKER_ID:${HOSTNAME:local}}");
        assertThat(environment.getProperty("spring.kafka.consumer.group-id")).startsWith("im-realtime-");
        assertThat(environment.getProperty("spring.kafka.consumer.auto-offset-reset")).isEqualTo("latest");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.service")).isEqualTo("im-realtime-worker");
        assertThat(rawProperty(environment, "im-realtime.yaml", "im.session.worker-id"))
                .isEqualTo("${IM_REALTIME_WORKER_ID:${HOSTNAME:local}}");
        assertThat(sessionProperties.getWorkerId()).isEqualTo("im-realtime-test");
        assertThat(rawProperty(environment, "im-realtime.yaml", "im.kafka.consumer.group-id"))
                .isEqualTo("im-realtime-${IM_REALTIME_WORKER_ID:${HOSTNAME:local}}");
        assertThat(environment.getProperty("im.kafka.consumer.group-id")).startsWith("im-realtime-");
    }

    private static StandardEnvironment environmentFrom(String fileName) throws Exception {
        Path path = seedFile(fileName);
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new YamlPropertySourceLoader().load(fileName, new FileSystemResource(path)).get(0));
        sources.addFirst(new MapPropertySource("test-worker-env", Map.of(
                "IM_REALTIME_WORKER_ID", "im-realtime-test",
                "IM_ROOM_FANOUT_WORKER_INBOX_SLOT", "0"
        )));
        return environment;
    }

    private static Object rawProperty(StandardEnvironment environment, String sourceName, String propertyName) {
        return environment.getPropertySources().get(sourceName).getProperty(propertyName);
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
