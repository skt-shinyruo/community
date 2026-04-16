package com.nowcoder.community.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTopologyProfileTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void baseConfigUsesStandaloneRedisDefaults() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        loader.load("application", new FileSystemResource("src/main/resources/application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));

        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6379");
        assertThat(environment.getProperty("spring.data.redis.cluster.nodes")).isNull();
    }

    @Test
    void redisClusterProfilePublishesClusterNodes() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getSystemProperties().put("SPRING_DATA_REDIS_CLUSTER_NODES", "redis-1:6379,redis-2:6379");
        loader.load("application", new FileSystemResource("src/main/resources/application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        loader.load("application-redis-cluster", new FileSystemResource("src/main/resources/application-redis-cluster.yml"))
                .forEach(source -> environment.getPropertySources().addFirst(source));

        assertThat(environment.getProperty("spring.data.redis.cluster.nodes"))
                .isEqualTo("redis-1:6379,redis-2:6379");
    }
}
