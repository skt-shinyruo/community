package com.nowcoder.community.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaBackboneConfigTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void applicationConfigDefinesKafkaBackboneDefaults() throws Exception {
        StandardEnvironment environment = environmentFrom("src/main/resources/application.yml");

        assertBackboneDefaults(environment);
        assertConsumerDefaults(environment);
    }

    @Test
    void testApplicationConfigDefinesKafkaBackboneDefaults() throws Exception {
        StandardEnvironment environment = environmentFrom("src/test/resources/application.yml");

        assertBackboneDefaults(environment);
        assertConsumerDefaults(environment);
    }

    @Test
    void nacosKafkaPolicyDefinesKafkaBackboneDefaults() throws Exception {
        StandardEnvironment environment = environmentFrom("../../deploy/nacos/config/community-kafka-policy.yaml");

        assertBackboneDefaults(environment);
        assertConsumerDefaults(environment);
    }

    private StandardEnvironment environmentFrom(String path) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        loader.load(path, new FileSystemResource(path))
                .forEach(source -> environment.getPropertySources().addLast(source));
        return environment;
    }

    private static void assertBackboneDefaults(StandardEnvironment environment) {
        assertThat(environment.containsProperty("content.events.publisher")).isFalse();
        assertThat(environment.getProperty("content.events.outbox-topic")).isEqualTo("eventbus.content");
        assertThat(environment.getProperty("content.events.kafka-topic")).isEqualTo("content.events");

        assertThat(environment.containsProperty("social.events.publisher")).isFalse();
        assertThat(environment.getProperty("social.events.outbox-topic")).isEqualTo("eventbus.social");
        assertThat(environment.getProperty("social.events.kafka-topic")).isEqualTo("social.events");

        assertThat(environment.containsProperty("user.events.publisher")).isFalse();
        assertThat(environment.getProperty("user.events.outbox-topic")).isEqualTo("eventbus.user");
        assertThat(environment.getProperty("user.events.kafka-topic")).isEqualTo("user.events");
        assertThat(environment.getProperty("events.outbox.enabled", Boolean.class)).isTrue();
    }

    private static void assertConsumerDefaults(StandardEnvironment environment) {
        assertThat(environment.getProperty("notice.kafka.consumer.group-id")).isEqualTo("notice-projection");
        assertThat(environment.getProperty("search.kafka.consumer.group-id")).isEqualTo("search-post-projection");
        assertThat(environment.getProperty("user.reward.kafka.consumer.group-id")).isEqualTo("user-reward-projection");
    }
}
