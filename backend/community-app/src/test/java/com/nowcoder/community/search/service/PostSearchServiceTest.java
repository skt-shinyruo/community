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
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
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
        UUID postId1 = uuid(101);
        UUID postId2 = uuid(102);
        UUID postId3 = uuid(150);
        UUID userId1 = uuid(7);
        UUID userId2 = uuid(8);
        UUID userId3 = uuid(9);
        UUID categoryId1 = uuid(3);
        UUID categoryId3 = uuid(5);

        when(postScanQueryApi.scanPosts(null, 2)).thenReturn(new PostScanView(
                List.of(
                        projection(postId1, userId1, categoryId1, List.of("java"), "title-1", "content-1", 0, 0, Instant.parse("2026-03-28T00:00:00Z"), 1.5),
                        projection(postId2, userId2, null, List.of("spring", "boot"), "title-2", "content-2", 1, 1, null, 2.5)
                ),
                postId2,
                true
        ));
        when(postScanQueryApi.scanPosts(postId2, 2)).thenReturn(new PostScanView(
                List.of(
                        projection(postId3, userId3, categoryId3, List.of(), "title-3", "content-3", 2, 0, Instant.parse("2026-03-29T00:00:00Z"), null)
                ),
                postId3,
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
        scanOrder.verify(postScanQueryApi).scanPosts(null, 2);
        scanOrder.verify(postScanQueryApi).scanPosts(postId2, 2);
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
                        tuple(postId1, userId1, categoryId1, List.of("java"), "title-1", "content-1", 0, 0, Instant.parse("2026-03-28T00:00:00Z"), 1.5),
                        tuple(postId2, userId2, null, List.of("spring", "boot"), "title-2", "content-2", 1, 1, null, 2.5),
                        tuple(postId3, userId3, categoryId3, List.of(), "title-3", "content-3", 2, 0, Instant.parse("2026-03-29T00:00:00Z"), null)
                );
    }

    @Test
    void clearAndReindexShouldUpsertMappedPayloadsToTargetIndexAndAdvanceCursor() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        PostIndexManager indexManager = mock(PostIndexManager.class);
        UUID postId1 = uuid(201);
        UUID postId2 = uuid(205);
        UUID userId1 = uuid(17);
        UUID userId2 = uuid(18);
        UUID categoryId1 = uuid(13);
        UUID categoryId2 = uuid(14);

        when(indexManager.createNewIndex()).thenReturn("community_posts_v20260329000000");
        when(postScanQueryApi.scanPosts(null, 2)).thenReturn(new PostScanView(
                List.of(
                        projection(postId1, userId1, categoryId1, List.of("search"), "title-a", "content-a", 0, 0, Instant.parse("2026-03-27T00:00:00Z"), 8.0)
                ),
                postId1,
                true
        ));
        when(postScanQueryApi.scanPosts(postId1, 2)).thenReturn(new PostScanView(
                List.of(
                        projection(postId2, userId2, categoryId2, List.of("arch"), "title-b", "content-b", 1, 0, Instant.parse("2026-03-29T01:00:00Z"), 9.0)
                ),
                postId2,
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
        scanOrder.verify(postScanQueryApi).scanPosts(null, 2);
        scanOrder.verify(postScanQueryApi).scanPosts(postId1, 2);
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
                        tuple(postId1, userId1, categoryId1, List.of("search"), "title-a", "content-a", 0, 0, Instant.parse("2026-03-27T00:00:00Z"), 8.0),
                        tuple(postId2, userId2, categoryId2, List.of("arch"), "title-b", "content-b", 1, 0, Instant.parse("2026-03-29T01:00:00Z"), 9.0)
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
            UUID postId,
            UUID userId,
            UUID categoryId,
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
