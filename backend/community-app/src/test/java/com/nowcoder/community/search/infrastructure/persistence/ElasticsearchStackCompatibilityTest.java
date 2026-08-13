package com.nowcoder.community.search.infrastructure.persistence;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ElasticsearchStackCompatibilityTest {

    private static final String CLIENT_VERSION = Objects.requireNonNull(
            System.getProperty("elasticsearch.client.version"),
            "Maven Surefire must provide elasticsearch.client.version"
    );

    @Container
    private static final GenericContainer<?> ELASTICSEARCH = new GenericContainer<>(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:" + CLIENT_VERSION)
    )
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2));

    @Test
    void resolvedJavaClientShouldCallTheMatchingElasticsearchServer() throws IOException {
        URI endpoint = URI.create("http://" + ELASTICSEARCH.getHost() + ":" + ELASTICSEARCH.getMappedPort(9200));
        try (Rest5Client restClient = Rest5Client.builder(endpoint).build();
             Rest5ClientTransport transport = new Rest5ClientTransport(restClient, new JacksonJsonpMapper())) {
            ElasticsearchClient client = new ElasticsearchClient(transport);

            HealthStatus status = client.cluster().health().status();

            assertThat(status).isIn(HealthStatus.Green, HealthStatus.Yellow);
        }
    }
}
