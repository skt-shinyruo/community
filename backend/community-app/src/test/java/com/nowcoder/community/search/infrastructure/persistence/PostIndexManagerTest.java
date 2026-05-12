package com.nowcoder.community.search.infrastructure.persistence;

import com.nowcoder.community.search.infrastructure.persistence.dataobject.EsPostDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PostIndexManagerTest {

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
                "postId", "title", "content", "categoryId", "tags", "score", "createTime"
        ));

        new PostIndexManager(operations, "community_posts_v").ensureAliasReady();

        verify(operations).indexOps(EsPostDocument.class);
        verify(aliasOps).exists();
        verify(aliasOps).getMapping();
        verifyNoMoreInteractions(operations, aliasOps);
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

        assertThatThrownBy(() -> new PostIndexManager(operations, "community_posts_v").ensureAliasReady())
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
        when(aliasOps.createMapping()).thenReturn(expectedMapping);
        when(aliasOps.getAliases(EsPostDocument.INDEX_ALIAS)).thenThrow(new ResourceNotFoundException("alias missing"));

        new PostIndexManager(operations, "community_posts_v").ensureAliasReady();

        verify(directIndexOps, never()).alias(any(AliasActions.class));
        verify(targetOps).create();
        verify(targetOps).putMapping(expectedMapping);

        ArgumentCaptor<AliasActions> actionsCaptor = ArgumentCaptor.forClass(AliasActions.class);
        verify(targetOps).alias(actionsCaptor.capture());
        assertThat(actionsCaptor.getValue().getActions())
                .anySatisfy(action -> assertThat(action).isInstanceOf(AliasAction.Add.class));
    }

    private static Map<String, Object> mappingWithFields(String... fields) {
        Document properties = Document.create();
        for (String field : fields) {
            properties.put(field, Document.create());
        }
        return Map.of("properties", properties);
    }

    private static boolean hasIndexName(IndexCoordinates coordinates, String indexName) {
        return coordinates != null && List.of(coordinates.getIndexNames()).contains(indexName);
    }

    private static boolean hasVersionedIndexName(IndexCoordinates coordinates) {
        if (coordinates == null) {
            return false;
        }
        return Set.of(coordinates.getIndexNames()).stream()
                .anyMatch(name -> name.startsWith("community_posts_v") && !"community_posts_v*".equals(name));
    }

    private static void assertFieldType(String fieldName, FieldType expectedType) throws NoSuchFieldException {
        Field field = EsPostDocument.class.getDeclaredField(fieldName).getAnnotation(Field.class);
        assertThat(field)
                .as("ES mapping annotation for %s", fieldName)
                .isNotNull();
        assertThat(field.type()).isEqualTo(expectedType);
    }
}
