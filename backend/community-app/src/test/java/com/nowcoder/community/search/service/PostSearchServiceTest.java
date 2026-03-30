package com.nowcoder.community.search.service;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.config.PostScanProperties;
import com.nowcoder.community.search.repo.PostIndexManager;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PostSearchServiceTest {

    @Test
    void clearAndReindexShouldUpsertMappedPayloadsAndAdvanceCursorWhenNoTargetIndex() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);

        when(postScanQueryApi.scanPosts(0, 2)).thenReturn(new PostScanView(
                List.of(
                        projection(101, 7, 3, List.of("java"), "title-1", "content-1", 0, 0, Instant.parse("2026-03-28T00:00:00Z"), 1.5),
                        projection(102, 8, null, List.of("spring", "boot"), "title-2", "content-2", 1, 1, null, 2.5)
                ),
                102,
                true
        ));
        when(postScanQueryApi.scanPosts(102, 2)).thenReturn(new PostScanView(
                List.of(
                        projection(150, 9, 5, List.of(), "title-3", "content-3", 2, 0, Instant.parse("2026-03-29T00:00:00Z"), null)
                ),
                150,
                false
        ));

        PostSearchService service = new PostSearchService(
                repository,
                postScanQueryApi,
                propertiesWithPageSize(2),
                providerOf(null)
        );

        int total = service.clearAndReindexFromContentService();

        ArgumentCaptor<PostPayload> payloadCaptor = ArgumentCaptor.forClass(PostPayload.class);
        verify(repository).clear();
        verify(repository, times(3)).upsert(payloadCaptor.capture());
        verify(repository, never()).upsertToIndex(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verifyNoMoreInteractions(repository);

        InOrder scanOrder = inOrder(postScanQueryApi);
        scanOrder.verify(postScanQueryApi).scanPosts(0, 2);
        scanOrder.verify(postScanQueryApi).scanPosts(102, 2);
        verifyNoMoreInteractions(postScanQueryApi);

        assertThat(total).isEqualTo(3);
        assertThat(payloadCaptor.getAllValues())
                .extracting(
                        PostPayload::getPostId,
                        PostPayload::getUserId,
                        PostPayload::getCategoryId,
                        PostPayload::getTags,
                        PostPayload::getTitle,
                        PostPayload::getContent,
                        PostPayload::getType,
                        PostPayload::getStatus,
                        PostPayload::getCreateTime,
                        PostPayload::getScore
                )
                .containsExactly(
                        tuple(101, 7, 3, List.of("java"), "title-1", "content-1", 0, 0, Instant.parse("2026-03-28T00:00:00Z"), 1.5),
                        tuple(102, 8, null, List.of("spring", "boot"), "title-2", "content-2", 1, 1, null, 2.5),
                        tuple(150, 9, 5, List.of(), "title-3", "content-3", 2, 0, Instant.parse("2026-03-29T00:00:00Z"), null)
                );
    }

    @Test
    void clearAndReindexShouldUpsertMappedPayloadsToTargetIndexAndAdvanceCursor() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        PostIndexManager indexManager = mock(PostIndexManager.class);

        when(indexManager.createNewIndex()).thenReturn("community_posts_v20260329000000");
        when(postScanQueryApi.scanPosts(0, 2)).thenReturn(new PostScanView(
                List.of(
                        projection(201, 17, 13, List.of("search"), "title-a", "content-a", 0, 0, Instant.parse("2026-03-27T00:00:00Z"), 8.0)
                ),
                201,
                true
        ));
        when(postScanQueryApi.scanPosts(201, 2)).thenReturn(new PostScanView(
                List.of(
                        projection(205, 18, 14, List.of("arch"), "title-b", "content-b", 1, 0, Instant.parse("2026-03-29T01:00:00Z"), 9.0)
                ),
                205,
                false
        ));

        PostSearchService service = new PostSearchService(
                repository,
                postScanQueryApi,
                propertiesWithPageSize(2),
                providerOf(indexManager)
        );

        int total = service.clearAndReindexFromContentService();

        ArgumentCaptor<PostPayload> payloadCaptor = ArgumentCaptor.forClass(PostPayload.class);
        verify(indexManager).ensureAliasReady();
        verify(indexManager).createNewIndex();
        verify(indexManager).switchAliasTo("community_posts_v20260329000000");
        verify(indexManager).cleanupOldIndices();
        verifyNoMoreInteractions(indexManager);

        verify(repository, never()).clear();
        verify(repository, times(2)).upsertToIndex(payloadCaptor.capture(), eq("community_posts_v20260329000000"));
        verify(repository, never()).upsert(org.mockito.ArgumentMatchers.any());
        verifyNoMoreInteractions(repository);

        InOrder scanOrder = inOrder(postScanQueryApi);
        scanOrder.verify(postScanQueryApi).scanPosts(0, 2);
        scanOrder.verify(postScanQueryApi).scanPosts(201, 2);
        verifyNoMoreInteractions(postScanQueryApi);

        assertThat(total).isEqualTo(2);
        assertThat(payloadCaptor.getAllValues())
                .extracting(
                        PostPayload::getPostId,
                        PostPayload::getUserId,
                        PostPayload::getCategoryId,
                        PostPayload::getTags,
                        PostPayload::getTitle,
                        PostPayload::getContent,
                        PostPayload::getType,
                        PostPayload::getStatus,
                        PostPayload::getCreateTime,
                        PostPayload::getScore
                )
                .containsExactly(
                        tuple(201, 17, 13, List.of("search"), "title-a", "content-a", 0, 0, Instant.parse("2026-03-27T00:00:00Z"), 8.0),
                        tuple(205, 18, 14, List.of("arch"), "title-b", "content-b", 1, 0, Instant.parse("2026-03-29T01:00:00Z"), 9.0)
                );
    }

    private static PostScanProperties propertiesWithPageSize(int pageSize) {
        PostScanProperties properties = new PostScanProperties();
        properties.setPageSize(pageSize);
        return properties;
    }

    private static ObjectProvider<PostIndexManager> providerOf(PostIndexManager indexManager) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (indexManager != null) {
            beanFactory.addBean("postIndexManager", indexManager);
        }
        return beanFactory.getBeanProvider(PostIndexManager.class);
    }

    private static PostScanView.PostProjectionView projection(
            int postId,
            int userId,
            Integer categoryId,
            List<String> tags,
            String title,
            String content,
            int type,
            int status,
            Instant createTime,
            Double score
    ) {
        return new PostScanView.PostProjectionView(
                postId,
                userId,
                categoryId,
                tags,
                title,
                content,
                type,
                status,
                createTime,
                score
        );
    }
}
