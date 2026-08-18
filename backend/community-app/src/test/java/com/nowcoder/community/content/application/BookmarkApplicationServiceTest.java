package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookmarkApplicationServiceTest {

    private static BookmarkCounterReconciliationPort reconciliationPort() {
        return mock(BookmarkCounterReconciliationPort.class);
    }

    private static BookmarkApplicationService service(
            BookmarkRepository bookmarkRepository,
            PostCounterCache postCounterCache,
            BookmarkCounterReconciliationPort reconciliationPort
    ) {
        return new BookmarkApplicationService(
                bookmarkRepository,
                postCounterCache,
                reconciliationPort,
                mock(PostFeedSummaryLoader.class)
        );
    }

    @Test
    void constructorShouldRejectMissingDurableReconciliationPort() {
        assertThatThrownBy(() -> new BookmarkApplicationService(
                mock(BookmarkRepository.class),
                mock(PostCounterCache.class),
                null,
                mock(PostFeedSummaryLoader.class)
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bookmarkCounterReconciliationPort");
    }

    @Test
    void listBookmarkedPostSummariesDelegatesProjectionToSummaryModule() {
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        PostFeedSummaryLoader summaryLoader = mock(PostFeedSummaryLoader.class);
        BookmarkApplicationService service = new BookmarkApplicationService(
                bookmarkRepository,
                mock(PostCounterCache.class),
                reconciliationPort(),
                summaryLoader
        );
        UUID userId = uuid(7);
        DiscussPost post = new DiscussPost();
        post.setId(uuid(11));
        post.setUserId(userId);
        post.setCreateTime(new Date());
        List<DiscussPost> posts = List.of(post);
        PostSummaryResult view = new PostSummaryResult(
                post.getId(), userId, "decoded title", "preview", 0, 0,
                post.getCreateTime(), 3, 9.5, uuid(2), List.of("spring"),
                null, null, post.getCreateTime(), "latest reply"
        );
        when(bookmarkRepository.listBookmarkedPosts(userId, 0, 10)).thenReturn(posts);
        when(summaryLoader.serveCurrentPosts(posts)).thenReturn(List.of(view));

        assertThat(service.listBookmarkedPostSummaries(userId, 0, 10)).containsExactly(view);

        verify(bookmarkRepository).listBookmarkedPosts(userId, 0, 10);
        verify(summaryLoader).serveCurrentPosts(posts);
    }

    @Test
    void addShouldMarkAuthoritativeBookmarkCountForRebuild() {
        BookmarkRepository repository = mock(BookmarkRepository.class);
        PostCounterCache counter = mock(PostCounterCache.class);
        BookmarkApplicationService service = service(repository, counter, reconciliationPort());
        UUID userId = uuid(9);
        UUID postId = uuid(10);
        when(repository.add(userId, postId)).thenReturn(true);

        service.add(userId, postId);

        verify(counter).markDirty(postId);
    }

    @Test
    void addShouldDeferCounterDirtyMarkerUntilDatabaseCommit() {
        BookmarkRepository repository = mock(BookmarkRepository.class);
        PostCounterCache counter = mock(PostCounterCache.class);
        BookmarkApplicationService service = service(repository, counter, reconciliationPort());
        UUID userId = uuid(27);
        UUID postId = uuid(28);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.add(userId, postId);
            verify(repository).add(userId, postId);
            verify(counter, never()).markDirty(postId);
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(counter).markDirty(postId);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void idempotentRemoveRetryShouldStillRepairBookmarkCounter() {
        BookmarkRepository repository = mock(BookmarkRepository.class);
        PostCounterCache counter = mock(PostCounterCache.class);
        BookmarkCounterReconciliationPort reconciliation = reconciliationPort();
        BookmarkApplicationService service = service(repository, counter, reconciliation);
        UUID userId = uuid(11);
        UUID postId = uuid(12);
        when(repository.remove(userId, postId)).thenReturn(false);

        service.remove(userId, postId);

        verify(reconciliation).recordMutation(postId);
        verify(counter).markDirty(postId);
    }

    @Test
    void retryAfterCacheFailureShouldReconcileEvenWhenBookmarkAlreadyExists() {
        BookmarkRepository repository = mock(BookmarkRepository.class);
        PostCounterCache counter = mock(PostCounterCache.class);
        BookmarkCounterReconciliationPort reconciliation = reconciliationPort();
        BookmarkApplicationService service = service(repository, counter, reconciliation);
        UUID userId = uuid(13);
        UUID postId = uuid(14);
        when(repository.add(userId, postId)).thenReturn(true, false);
        doThrow(new IllegalStateException("redis unavailable"))
                .doNothing().when(counter).markDirty(postId);

        assertThatCode(() -> service.add(userId, postId)).doesNotThrowAnyException();
        service.add(userId, postId);

        verify(repository, times(2)).add(userId, postId);
        verify(reconciliation, times(2)).recordMutation(postId);
        verify(counter, times(2)).markDirty(postId);
    }

    @Test
    void addShouldPersistDurableMutationBeforeBestEffortAfterCommitCacheRepair() {
        BookmarkRepository repository = mock(BookmarkRepository.class);
        PostCounterCache counter = mock(PostCounterCache.class);
        BookmarkCounterReconciliationPort reconciliation = reconciliationPort();
        BookmarkApplicationService service = service(repository, counter, reconciliation);
        UUID postId = uuid(16);
        doThrow(new IllegalStateException("redis unavailable")).when(counter).markDirty(postId);

        service.add(uuid(15), postId);

        verify(reconciliation).recordMutation(postId);
        verify(counter).markDirty(postId);
    }
}
