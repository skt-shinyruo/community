package com.nowcoder.community.content.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ContentReadModelsAfterCommitTest {

    private static final UUID POST_ID = uuid(8801);
    private static final UUID BOARD_ID = uuid(8802);
    private static final String COMMENT_BODY = "comment body must not be logged";

    private PostCounterCache postCounterCache;
    private CommentPageCache commentPageCache;
    private PostFeedCache postFeedCache;
    private PostSummaryCache postSummaryCache;
    private PostDetailCache postDetailCache;
    private ContentReadModelsAfterCommit readModels;

    @BeforeEach
    void setUp() {
        postCounterCache = mock(PostCounterCache.class);
        commentPageCache = mock(CommentPageCache.class);
        postFeedCache = mock(PostFeedCache.class);
        postSummaryCache = mock(PostSummaryCache.class);
        postDetailCache = mock(PostDetailCache.class);
        readModels = new ContentReadModelsAfterCommit(
                postCounterCache,
                commentPageCache,
                new PostCacheAfterCommit(postFeedCache, postSummaryCache, postDetailCache)
        );
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void commentCreatedFansOutOnlyAfterCommit() {
        beginTransactionSynchronization();

        readModels.commentCreated(POST_ID, 17L);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(5);
        verifyNoCacheInteractions();

        commitTransactionSynchronization();

        verify(postCounterCache).markDirty(POST_ID);
        verify(commentPageCache).evictPost(POST_ID);
        verify(postFeedCache).remove(POST_ID, null, 17L);
        verify(postSummaryCache).evictAll(List.of(POST_ID), 17L);
        verify(postDetailCache).evict(POST_ID, 17L);
    }

    @Test
    void rollbackSuppressesEveryCommentReadModelAction() {
        beginTransactionSynchronization();

        readModels.commentDeleted(POST_ID, 18L);
        rollbackTransactionSynchronization();

        verifyNoCacheInteractions();
    }

    @Test
    void commentEditKeepsCounterAndFeedMembership() {
        beginTransactionSynchronization();

        readModels.commentEdited(POST_ID, 19L);
        commitTransactionSynchronization();

        verify(commentPageCache).evictPost(POST_ID);
        verify(postSummaryCache).evictAll(List.of(POST_ID), 19L);
        verify(postDetailCache).evict(POST_ID, 19L);
        verifyNoInteractions(postCounterCache, postFeedCache);
    }

    @Test
    void postDeleteUsesTerminalFences() {
        beginTransactionSynchronization();

        readModels.postDeleted(POST_ID, BOARD_ID, 20L);
        commitTransactionSynchronization();

        verify(postFeedCache).terminalRemove(POST_ID, BOARD_ID, 20L);
        verify(postSummaryCache).terminalEvict(POST_ID, 20L);
        verify(postDetailCache).terminalEvict(POST_ID, 20L);
        verifyNoInteractions(postCounterCache, commentPageCache);
    }

    @Test
    void oneSinkFailureDoesNotBlockOtherSinksOrLogCommentBody() {
        doThrow(new IllegalStateException(COMMENT_BODY)).when(postCounterCache).markDirty(POST_ID);
        LogCapture logs = startLogCapture();
        try {
            beginTransactionSynchronization();
            readModels.commentCreated(POST_ID, 21L);

            assertThatCode(this::commitTransactionSynchronization).doesNotThrowAnyException();

            verify(commentPageCache).evictPost(POST_ID);
            verify(postFeedCache).remove(POST_ID, null, 21L);
            verify(postSummaryCache).evictAll(List.of(POST_ID), 21L);
            verify(postDetailCache).evict(POST_ID, 21L);
            assertWarning(logs, "comment-count-dirty");
        } finally {
            stopLogCapture(logs);
        }
    }

    @Test
    void noTransactionExecutesActionsImmediately() {
        readModels.postUpdated(POST_ID, 22L);

        verify(postFeedCache).remove(POST_ID, null, 22L);
        verify(postSummaryCache).evictAll(List.of(POST_ID), 22L);
        verify(postDetailCache).evict(POST_ID, 22L);
        verify(commentPageCache, never()).evictPost(POST_ID);
    }

    private void verifyNoCacheInteractions() {
        verifyNoInteractions(postCounterCache, commentPageCache, postFeedCache, postSummaryCache, postDetailCache);
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private void commitTransactionSynchronization() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    private void rollbackTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private LogCapture startLogCapture() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ContentReadModelsAfterCommit.class);
        boolean wasAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setAdditive(false);
        logger.addAppender(appender);
        return new LogCapture(logger, appender, wasAdditive);
    }

    private void stopLogCapture(LogCapture logs) {
        try {
            logs.logger().detachAppender(logs.appender());
        } finally {
            logs.logger().setAdditive(logs.wasAdditive());
            logs.appender().stop();
        }
    }

    private void assertWarning(LogCapture logs, String operation) {
        assertThat(logs.appender().list).hasSize(1);
        ILoggingEvent warning = logs.appender().list.get(0);
        assertThat(warning.getLevel()).isEqualTo(Level.WARN);
        assertThat(warning.getFormattedMessage())
                .contains("operation=" + operation)
                .contains("postId=" + POST_ID)
                .doesNotContain(COMMENT_BODY);
        assertThat(warning.getArgumentArray()).containsExactly(operation, POST_ID);
        assertThat(warning.getThrowableProxy()).isNull();
    }

    private record LogCapture(Logger logger, ListAppender<ILoggingEvent> appender, boolean wasAdditive) {
    }
}
