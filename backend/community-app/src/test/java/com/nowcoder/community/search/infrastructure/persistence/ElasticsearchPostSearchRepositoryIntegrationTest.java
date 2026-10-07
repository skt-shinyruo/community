package com.nowcoder.community.search.infrastructure.persistence;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchHit;
import com.nowcoder.community.search.domain.model.PostSearchQuery;
import com.nowcoder.community.search.infrastructure.persistence.dataobject.EsPostDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ElasticsearchPostSearchRepositoryIntegrationTest {

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
    void hyphenatedTitleIsFoundByAnalyzedMatch() throws IOException {
        try (SearchStack stack = SearchStack.open(ELASTICSEARCH)) {
            UUID postId = uuid(217);
            stack.save(post(
                    postId,
                    "manual-check-20261007-post",
                    "A walkthrough of the manual check.",
                    0
            ));
            stack.save(post(uuid(301), "garden notes", "tomatoes and soil", 0));

            List<PostSearchHit> hits = stack.search("manual-check-20261007-post");

            assertThat(hits).extracting(PostSearchHit::postId).containsExactly(postId);
            assertThat(hits.get(0).highlightedTitle()).contains("<em>");
        }
    }

    @Test
    void titleTokenAndBodyOnlyWordEachFindThePost() throws IOException {
        try (SearchStack stack = SearchStack.open(ELASTICSEARCH)) {
            UUID postId = uuid(218);
            stack.save(post(
                    postId,
                    uuid(31),
                    List.of("search-matrix"),
                    "manual-check-20261007-post",
                    "A walkthrough of the manual check.",
                    0
            ));
            stack.save(post(uuid(302), "garden notes", "tomatoes and soil", 0));

            assertThat(stack.search("check")).extracting(PostSearchHit::postId).containsExactly(postId);
            assertThat(stack.search("20261007")).extracting(PostSearchHit::postId).containsExactly(postId);
            assertThat(stack.search("walkthrough"))
                    .singleElement()
                    .satisfies(hit -> {
                        assertThat(hit.postId()).isEqualTo(postId);
                        assertThat(hit.highlightedContent()).contains("<em>");
                    });
            assertThat(stack.search("manual check")).extracting(PostSearchHit::postId).containsExactly(postId);
        }
    }

    @Test
    void emptyKeywordMatchesAllActivePostsAndStillAppliesTaxonomyFilters() throws IOException {
        try (SearchStack stack = SearchStack.open(ELASTICSEARCH)) {
            UUID inCategory = uuid(219);
            UUID otherCategory = uuid(220);
            UUID deleted = uuid(221);
            stack.save(post(inCategory, uuid(41), List.of("kept-tag"), "visible title", "visible body", 0));
            stack.save(post(otherCategory, uuid(42), List.of("other-tag"), "other title", "other body", 0));
            stack.save(post(deleted, uuid(41), List.of("kept-tag"), "deleted title", "deleted body", 2));

            assertThat(stack.search("")).extracting(PostSearchHit::postId)
                    .containsExactlyInAnyOrder(inCategory, otherCategory);
            assertThat(stack.search("deleted")).isEmpty();
            assertThat(stack.search("", uuid(41), null)).extracting(PostSearchHit::postId)
                    .containsExactly(inCategory);
            assertThat(stack.search("", null, "kept-tag")).extracting(PostSearchHit::postId)
                    .containsExactly(inCategory);
            assertThat(stack.search("zzznomatch217")).isEmpty();
        }
    }

    private static PostSearchDocument post(UUID postId, String title, String content, int status) {
        return post(postId, uuid(3), List.of("java"), title, content, status);
    }

    private static PostSearchDocument post(
            UUID postId,
            UUID categoryId,
            List<String> tags,
            String title,
            String content,
            int status
    ) {
        return new PostSearchDocument(
                postId,
                uuid(7),
                categoryId,
                tags,
                title,
                content,
                0,
                status,
                1L,
                1L,
                Instant.parse("2026-10-07T00:00:00Z"),
                1.0
        );
    }

    private static final class SearchStack implements AutoCloseable {

        private final Rest5Client restClient;
        private final Rest5ClientTransport transport;
        private final ElasticsearchOperations operations;
        private final ElasticsearchPostSearchRepository repository;

        private SearchStack(
                Rest5Client restClient,
                Rest5ClientTransport transport,
                ElasticsearchOperations operations,
                ElasticsearchPostSearchRepository repository
        ) {
            this.restClient = restClient;
            this.transport = transport;
            this.operations = operations;
            this.repository = repository;
        }

        private static SearchStack open(GenericContainer<?> elasticsearch) {
            URI endpoint = URI.create("http://" + elasticsearch.getHost() + ":" + elasticsearch.getMappedPort(9200));
            Rest5Client restClient = Rest5Client.builder(endpoint).build();
            Rest5ClientTransport transport = new Rest5ClientTransport(restClient, new JacksonJsonpMapper());
            ElasticsearchOperations operations = new ElasticsearchTemplate(new ElasticsearchClient(transport));
            new PostIndexManager(operations, EsPostDocument.INDEX_PREFIX, 2, Clock.systemUTC()).ensureAliasReady();
            SearchStack stack = new SearchStack(
                    restClient,
                    transport,
                    operations,
                    new ElasticsearchPostSearchRepository(operations)
            );
            stack.clear();
            return stack;
        }

        private void clear() {
            operations.delete(
                    DeleteQuery.builder(NativeQuery.builder()
                            .withQuery(query -> query.matchAll(matchAll -> matchAll))
                            .build()).build(),
                    EsPostDocument.class
            );
            operations.indexOps(IndexCoordinates.of(EsPostDocument.INDEX_ALIAS)).refresh();
        }

        private void save(PostSearchDocument document) {
            repository.save(document);
            operations.indexOps(IndexCoordinates.of(EsPostDocument.INDEX_ALIAS)).refresh();
        }

        private List<PostSearchHit> search(String keyword) {
            return search(keyword, null, null);
        }

        private List<PostSearchHit> search(String keyword, UUID categoryId, String tag) {
            return repository.search(new PostSearchQuery(keyword, categoryId, tag, 0, 10));
        }

        @Override
        public void close() throws IOException {
            transport.close();
            restClient.close();
        }
    }
}
