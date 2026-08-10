package com.nowcoder.community.search.infrastructure.persistence;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import com.nowcoder.community.search.infrastructure.persistence.dataobject.EsPostDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexInformation;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PostIndexManagerTest {

    private static final Clock TEST_CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T12:34:56Z"),
            ZoneOffset.UTC
    );

    @Test
    void documentMappingShouldDeclareSearchAndSortFields() throws NoSuchFieldException {
        assertFieldType("postId", FieldType.Keyword);
        assertFieldType("userId", FieldType.Keyword);
        assertFieldType("categoryId", FieldType.Keyword);
        assertFieldType("tags", FieldType.Keyword);
        assertFieldType("title", FieldType.Text);
        assertFieldType("content", FieldType.Text);
        assertFieldType("type", FieldType.Integer);
        assertFieldType("status", FieldType.Integer);
        assertFieldType("aggregateVersion", FieldType.Long);
        assertFieldType("scoreVersion", FieldType.Long);
        assertFieldType("createTime", FieldType.Long);
        assertFieldType("score", FieldType.Double);
    }

    @Test
    void ensureAliasReadyShouldKeepExistingAliasWhenMappingSupportsSearch() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(aliasOps.exists()).thenReturn(true);
        when(aliasOps.getMapping()).thenReturn(mappingWithFields(
                "postId", "title", "content", "categoryId", "tags", "status", "aggregateVersion",
                "scoreVersion", "score", "createTime"
        ));

        manager(operations, "community_posts_v", 2).ensureAliasReady();

        verify(operations).indexOps(EsPostDocument.class);
        verify(aliasOps).exists();
        verify(aliasOps).getMapping();
        verifyNoMoreInteractions(operations, aliasOps);
    }

    @Test
    void ensureAliasReadyShouldAddVersionFieldsToAnExistingAlias() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        Document expectedMapping = Document.create();
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(aliasOps.exists()).thenReturn(true);
        when(aliasOps.getMapping()).thenReturn(mappingWithFields(
                "postId", "title", "content", "categoryId", "tags", "status", "score", "createTime"
        ));
        when(aliasOps.createMapping()).thenReturn(expectedMapping);
        when(aliasOps.putMapping(expectedMapping)).thenReturn(true);

        manager(operations, "community_posts_v", 2).ensureAliasReady();

        verify(aliasOps).putMapping(expectedMapping);
    }

    @Test
    void ensureAliasReadyShouldRejectExistingAliasWhenMappingIsMissingRequiredField() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(aliasOps.exists()).thenReturn(true);
        when(aliasOps.getMapping()).thenReturn(mappingWithFields(
                "postId", "title", "content", "categoryId", "tags", "createTime"
        ));

        assertThatThrownBy(() -> manager(operations, "community_posts_v", 2).ensureAliasReady())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("community_posts_alias")
                .hasMessageContaining("score");

        verify(aliasOps).exists();
        verify(aliasOps).getMapping();
        verify(operations, never()).indexOps(argThat((IndexCoordinates coordinates) -> hasVersionedIndexName(coordinates)));
    }

    @Test
    void ensureAliasReadyShouldCreateVersionedIndexForFreshAlias() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        IndexOperations directIndexOps = mock(IndexOperations.class);
        IndexOperations wildcardOps = mock(IndexOperations.class);
        IndexOperations targetOps = mock(IndexOperations.class);
        Document expectedMapping = Document.create();
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexName(coordinates, "community_posts"))))
                .thenReturn(directIndexOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexName(coordinates, "community_posts_v*"))))
                .thenReturn(wildcardOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasVersionedIndexName(coordinates))))
                .thenReturn(targetOps);
        when(aliasOps.exists()).thenReturn(false);
        when(directIndexOps.exists()).thenReturn(true);
        when(wildcardOps.exists()).thenReturn(false);
        when(targetOps.exists()).thenReturn(false);
        when(targetOps.create()).thenReturn(true);
        when(aliasOps.createMapping()).thenReturn(expectedMapping);
        when(targetOps.putMapping(expectedMapping)).thenReturn(true);
        when(targetOps.alias(any(AliasActions.class))).thenReturn(true);
        when(aliasOps.getAliases(EsPostDocument.INDEX_ALIAS)).thenThrow(new ResourceNotFoundException("alias missing"));

        manager(operations, "community_posts_v", 2).ensureAliasReady();

        verify(directIndexOps, never()).alias(any(AliasActions.class));
        verify(targetOps).create();
        verify(targetOps).putMapping(expectedMapping);

        ArgumentCaptor<AliasActions> actionsCaptor = ArgumentCaptor.forClass(AliasActions.class);
        verify(targetOps).alias(actionsCaptor.capture());
        assertThat(actionsCaptor.getValue().getActions())
                .anySatisfy(action -> assertThat(action).isInstanceOf(AliasAction.Add.class));
    }

    @Test
    void switchAliasShouldFailWhenElasticsearchDoesNotAcknowledgeTheAtomicUpdate() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        IndexOperations targetOps = mock(IndexOperations.class);
        String target = "community_posts_v20260803010101";
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexName(coordinates, target))))
                .thenReturn(targetOps);
        when(aliasOps.getAliases(EsPostDocument.INDEX_ALIAS)).thenReturn(Map.of());
        when(targetOps.alias(any(AliasActions.class))).thenReturn(false);

        assertThatThrownBy(() -> manager(operations, "community_posts_v", 2).switchAliasTo(target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not acknowledged");
    }

    @Test
    void createNewIndexShouldDeleteThePartialIndexWhenMappingIsNotAcknowledged() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        IndexOperations targetOps = mock(IndexOperations.class);
        Document mapping = Document.create();
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasVersionedIndexName(coordinates))))
                .thenReturn(targetOps);
        when(targetOps.exists()).thenReturn(false);
        when(targetOps.create()).thenReturn(true);
        when(aliasOps.createMapping()).thenReturn(mapping);
        when(targetOps.putMapping(mapping)).thenReturn(false);
        when(targetOps.delete()).thenReturn(true);

        assertThatThrownBy(() -> manager(operations, "community_posts_v", 2).createNewIndex())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mapping was not acknowledged");

        verify(targetOps).delete();
    }

    @Test
    void createNewIndexShouldRetryWithSuffixWhenCreateLosesTheNameRace() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        IndexOperations baseOps = mock(IndexOperations.class);
        IndexOperations suffixedOps = mock(IndexOperations.class);
        Document mapping = Document.create();
        RuntimeException conflict = indexNameConflict();
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexNameMatching(
                coordinates, "community_posts_v\\d{14}"
        )))).thenReturn(baseOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexNameMatching(
                coordinates, "community_posts_v\\d{14}_1"
        )))).thenReturn(suffixedOps);
        when(baseOps.exists()).thenReturn(false);
        when(baseOps.create()).thenThrow(conflict);
        when(suffixedOps.exists()).thenReturn(false);
        when(suffixedOps.create()).thenReturn(true);
        when(aliasOps.createMapping()).thenReturn(mapping);
        when(suffixedOps.putMapping(mapping)).thenReturn(true);

        String indexName = manager(operations, "community_posts_v", 2).createNewIndex();

        assertThat(indexName).isEqualTo("community_posts_v20260810123456_1");
        verify(baseOps).create();
        verify(suffixedOps).create();
        verify(suffixedOps).putMapping(mapping);
    }

    @Test
    void createNewIndexShouldRetryWithSuffixWhenAnUnacknowledgedCreateNowExists() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        IndexOperations baseOps = mock(IndexOperations.class);
        IndexOperations suffixedOps = mock(IndexOperations.class);
        Document mapping = Document.create();
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexNameMatching(
                coordinates, "community_posts_v\\d{14}"
        )))).thenReturn(baseOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexNameMatching(
                coordinates, "community_posts_v\\d{14}_1"
        )))).thenReturn(suffixedOps);
        when(baseOps.exists()).thenReturn(false, true);
        when(baseOps.create()).thenReturn(false);
        when(suffixedOps.exists()).thenReturn(false);
        when(suffixedOps.create()).thenReturn(true);
        when(aliasOps.createMapping()).thenReturn(mapping);
        when(suffixedOps.putMapping(mapping)).thenReturn(true);

        String indexName = manager(operations, "community_posts_v", 2).createNewIndex();

        assertThat(indexName).matches("community_posts_v\\d{14}_1");
        verify(baseOps, times(2)).exists();
        verify(suffixedOps).create();
    }

    @Test
    void createNewIndexShouldFailWhenAnUnacknowledgedCreateStillDoesNotExist() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations baseOps = mock(IndexOperations.class);
        IndexOperations suffixedOps = mock(IndexOperations.class);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexNameMatching(
                coordinates, "community_posts_v\\d{14}"
        )))).thenReturn(baseOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexNameMatching(
                coordinates, "community_posts_v\\d{14}_1"
        )))).thenReturn(suffixedOps);
        when(baseOps.exists()).thenReturn(false);
        when(baseOps.create()).thenReturn(false);

        assertThatThrownBy(() -> manager(operations, "community_posts_v", 2).createNewIndex())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("creation was not acknowledged");

        verify(baseOps).create();
        verify(baseOps, times(2)).exists();
        verifyNoMoreInteractions(suffixedOps);
    }

    @Test
    void cleanupShouldKeepTheActiveIndexAndConfiguredNumberOfRetiredIndices() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        IndexOperations wildcardOps = mock(IndexOperations.class);
        IndexOperations oldestOps = mock(IndexOperations.class);
        String oldest = "community_posts_v20260801010101";
        String active = "community_posts_v20260802010101";
        String newestRetired = "community_posts_v20260803010101";
        String unrelated = "community_posts_vendor_data";
        List<IndexInformation> information = List.of(
                indexInformation(oldest),
                indexInformation(active),
                indexInformation(newestRetired),
                indexInformation(unrelated)
        );
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexName(
                coordinates, "community_posts_v*"
        )))).thenReturn(wildcardOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexName(coordinates, oldest))))
                .thenReturn(oldestOps);
        when(aliasOps.getAliases(EsPostDocument.INDEX_ALIAS)).thenReturn(Map.of(active, Set.of()));
        when(wildcardOps.getInformation()).thenReturn(information);
        when(oldestOps.exists()).thenReturn(true);
        when(oldestOps.delete()).thenReturn(true);

        manager(operations, "community_posts_v", 1).cleanupOldIndices();

        verify(oldestOps).delete();
        verify(operations, never()).indexOps(argThat(
                (IndexCoordinates coordinates) -> hasIndexName(coordinates, unrelated)
        ));
    }

    @Test
    void cleanupShouldCompareSameSecondSuffixesNumerically() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations aliasOps = mock(IndexOperations.class);
        IndexOperations wildcardOps = mock(IndexOperations.class);
        IndexOperations suffixNineOps = mock(IndexOperations.class);
        IndexOperations suffixTenOps = mock(IndexOperations.class);
        String active = "community_posts_v20260803010101";
        String suffixNine = active + "_9";
        String suffixTen = active + "_10";
        List<IndexInformation> information = List.of(
                indexInformation(suffixNine),
                indexInformation(active),
                indexInformation(suffixTen)
        );
        when(operations.indexOps(EsPostDocument.class)).thenReturn(aliasOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexName(
                coordinates, "community_posts_v*"
        )))).thenReturn(wildcardOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexName(coordinates, suffixNine))))
                .thenReturn(suffixNineOps);
        when(operations.indexOps(argThat((IndexCoordinates coordinates) -> hasIndexName(coordinates, suffixTen))))
                .thenReturn(suffixTenOps);
        when(aliasOps.getAliases(EsPostDocument.INDEX_ALIAS)).thenReturn(Map.of(active, Set.of()));
        when(wildcardOps.getInformation()).thenReturn(information);
        when(suffixNineOps.exists()).thenReturn(true);
        when(suffixNineOps.delete()).thenReturn(true);
        when(suffixTenOps.exists()).thenReturn(true);
        when(suffixTenOps.delete()).thenReturn(true);

        manager(operations, "community_posts_v", 1).cleanupOldIndices();

        verify(suffixNineOps).delete();
        verify(suffixTenOps, never()).delete();
    }

    private static Map<String, Object> mappingWithFields(String... fields) {
        Document properties = Document.create();
        for (String field : fields) {
            properties.put(field, Document.create());
        }
        return Map.of("properties", properties);
    }

    private static PostIndexManager manager(
            ElasticsearchOperations operations,
            String indexPrefix,
            int keepHistory
    ) {
        return new PostIndexManager(operations, indexPrefix, keepHistory, TEST_CLOCK);
    }

    private static boolean hasIndexName(IndexCoordinates coordinates, String indexName) {
        return coordinates != null && List.of(coordinates.getIndexNames()).contains(indexName);
    }

    private static boolean hasIndexNameMatching(IndexCoordinates coordinates, String pattern) {
        return coordinates != null && Set.of(coordinates.getIndexNames()).stream().anyMatch(name -> name.matches(pattern));
    }

    private static boolean hasVersionedIndexName(IndexCoordinates coordinates) {
        if (coordinates == null) {
            return false;
        }
        return Set.of(coordinates.getIndexNames()).stream()
                .anyMatch(name -> name.startsWith("community_posts_v") && !"community_posts_v*".equals(name));
    }

    private static IndexInformation indexInformation(String name) {
        IndexInformation information = mock(IndexInformation.class);
        when(information.getName()).thenReturn(name);
        return information;
    }

    private static RuntimeException indexNameConflict() {
        ErrorCause error = ErrorCause.of(builder -> builder
                .type("resource_already_exists_exception")
                .reason("index already exists"));
        ErrorResponse response = ErrorResponse.of(builder -> builder.error(error).status(400));
        ElasticsearchException elasticsearchFailure = new ElasticsearchException("indices.create", response);
        return new UncategorizedElasticsearchException(
                "index already exists",
                400,
                "{\"error\":{\"type\":\"resource_already_exists_exception\"}}",
                elasticsearchFailure
        );
    }

    private static void assertFieldType(String fieldName, FieldType expectedType) throws NoSuchFieldException {
        Field field = EsPostDocument.class.getDeclaredField(fieldName).getAnnotation(Field.class);
        assertThat(field)
                .as("ES mapping annotation for %s", fieldName)
                .isNotNull();
        assertThat(field.type()).isEqualTo(expectedType);
    }
}
