package com.nowcoder.community.search.infrastructure.persistence;

import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchQuery;
import com.nowcoder.community.search.domain.repository.PostSearchRepository;
import com.nowcoder.community.search.infrastructure.persistence.dataobject.EsPostDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ElasticsearchPostSearchRepositoryTest {

    @Test
    void elasticsearchRuntimeProvidesTheSearchRepositoryAndReindexRegistry() {
        new ApplicationContextRunner()
                .withBean(ElasticsearchOperations.class, () -> mock(ElasticsearchOperations.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withUserConfiguration(SearchRuntimeConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PostSearchRepository.class);
                    assertThat(context).hasSingleBean(SearchReindexTargetRegistry.class);
                });
    }

    @Test
    void saveShouldUseIndependentAggregateAndScoreVersionCompareAndSet() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        ElasticsearchPostSearchRepository repository = new ElasticsearchPostSearchRepository(operations);

        repository.save(activeDocument(uuid(101), 7L));

        ArgumentCaptor<UpdateQuery> queryCaptor = ArgumentCaptor.forClass(UpdateQuery.class);
        ArgumentCaptor<IndexCoordinates> indexCaptor = ArgumentCaptor.forClass(IndexCoordinates.class);
        verify(operations).update(queryCaptor.capture(), indexCaptor.capture());

        UpdateQuery update = queryCaptor.getValue();
        assertThat(indexCaptor.getValue().getIndexNames()).containsExactly(EsPostDocument.INDEX_ALIAS);
        assertThat(update.getParams()).containsEntry("aggregateVersion", 7L);
        assertThat(update.getParams()).containsEntry("scoreVersion", 3L);
        assertThat(update.getScript())
                .contains("if (ctx.op == 'create')")
                .contains("params.aggregateVersion > ctx._source.aggregateVersion")
                .contains("params.scoreVersion > ctx._source.scoreVersion")
                .contains("ctx._source.score = params.document.score")
                .contains("ctx.op = 'noop'");
        assertThat(update.getScript().indexOf("if (ctx.op == 'create')"))
                .isLessThan(update.getScript().indexOf("params.aggregateVersion > ctx._source.aggregateVersion"));
        assertThat(update.getScriptName()).isNull();
        assertThat(update.getLang()).isEqualTo("painless");
        assertThat(update.getRetryOnConflict()).isEqualTo(5);
        assertThat(update.getScriptedUpsert()).isTrue();
        assertThat(update.getUpsert())
                .containsEntry("postId", uuid(101).toString())
                .containsEntry("aggregateVersion", 7L)
                .containsEntry("scoreVersion", 3L)
                .containsEntry("status", 0);
    }

    @Test
    void tombstoneShouldUseTheSameVersionGuardAndRemainInTheIndex() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        ElasticsearchPostSearchRepository repository = new ElasticsearchPostSearchRepository(operations);
        UUID postId = uuid(102);

        repository.tombstone(postId, 9L);

        ArgumentCaptor<UpdateQuery> queryCaptor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(operations).update(
                queryCaptor.capture(),
                eq(IndexCoordinates.of(EsPostDocument.INDEX_ALIAS))
        );
        UpdateQuery update = queryCaptor.getValue();
        Document tombstone = (Document) update.getParams().get("document");
        assertThat(update.getParams()).containsEntry("aggregateVersion", 9L);
        assertThat(tombstone)
                .containsEntry("postId", postId.toString())
                .containsEntry("aggregateVersion", 9L)
                .containsEntry("status", 2)
                .containsEntry("tags", List.of());
        assertThat(tombstone.get("title")).isNull();
    }

    @Test
    void saveShouldWriteTheAliasAndTheCapturedRebuildTarget() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        SearchReindexTargetRegistry targetRegistry = mock(SearchReindexTargetRegistry.class);
        when(targetRegistry.currentIndex()).thenReturn(Optional.of("community_posts_v20260803010101"));
        ElasticsearchPostSearchRepository repository = new ElasticsearchPostSearchRepository(
                operations, targetRegistry
        );

        repository.save(activeDocument(uuid(103), 4L));

        ArgumentCaptor<IndexCoordinates> indexCaptor = ArgumentCaptor.forClass(IndexCoordinates.class);
        verify(operations, times(2)).update(any(UpdateQuery.class), indexCaptor.capture());
        assertThat(indexCaptor.getAllValues())
                .extracting(coordinates -> List.of(coordinates.getIndexNames()))
                .containsExactly(
                        List.of(EsPostDocument.INDEX_ALIAS),
                        List.of("community_posts_v20260803010101")
                );
    }

    @Test
    void saveShouldFailBeforeWritingWhenTheRebuildRegistryCannotBeRead() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        SearchReindexTargetRegistry targetRegistry = mock(SearchReindexTargetRegistry.class);
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(targetRegistry.currentIndex()).thenThrow(failure);
        ElasticsearchPostSearchRepository repository = new ElasticsearchPostSearchRepository(
                operations, targetRegistry
        );

        assertThatThrownBy(() -> repository.save(activeDocument(uuid(104), 4L)))
                .isSameAs(failure);
        verifyNoInteractions(operations);
    }

    @Test
    void saveAllToIndexShouldUseOneBoundedBulkUpdateForAReindexPage() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        ElasticsearchPostSearchRepository repository = new ElasticsearchPostSearchRepository(operations);
        PostSearchDocument first = activeDocument(uuid(105), 4L);
        PostSearchDocument second = activeDocument(uuid(106), 5L);
        String target = "community_posts_v20260803010101";

        repository.saveAllToIndex(List.of(first, second), target);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<List<UpdateQuery>> updatesCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(operations).bulkUpdate(updatesCaptor.capture(), eq(IndexCoordinates.of(target)));
        assertThat(updatesCaptor.getValue())
                .extracting(UpdateQuery::getId)
                .containsExactly(uuid(105).toString(), uuid(106).toString());
        assertThat(updatesCaptor.getValue())
                .allSatisfy(update -> assertThat(update.getScript()).contains("aggregateVersion"));
    }

    @Test
    void searchShouldExcludePersistentDeletionTombstones() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        @SuppressWarnings("unchecked")
        SearchHits<EsPostDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(Query.class), eq(EsPostDocument.class))).thenReturn(hits);
        ElasticsearchPostSearchRepository repository = new ElasticsearchPostSearchRepository(operations);

        assertThat(repository.search(new PostSearchQuery("", null, null, 0, 10))).isEmpty();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(operations).search(queryCaptor.capture(), eq(EsPostDocument.class));
        Criteria statusCriteria = ((CriteriaQuery) queryCaptor.getValue()).getCriteria().getCriteriaChain().stream()
                .filter(criteria -> criteria.getField() != null && "status".equals(criteria.getField().getName()))
                .findFirst()
                .orElseThrow();
        assertThat(statusCriteria.isNegating()).isTrue();
        assertThat(statusCriteria.getQueryCriteriaEntries())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getKey()).isEqualTo(Criteria.OperationKey.EQUALS);
                    assertThat(entry.getValue()).isEqualTo(2);
                });
    }

    private static PostSearchDocument activeDocument(UUID postId, long aggregateVersion) {
        return new PostSearchDocument(
                postId,
                uuid(7),
                uuid(3),
                List.of("java"),
                "title",
                "content",
                0,
                0,
                aggregateVersion,
                3L,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ElasticsearchPostSearchRepository.class, SearchReindexTargetRegistry.class})
    static class SearchRuntimeConfiguration {
    }
}
