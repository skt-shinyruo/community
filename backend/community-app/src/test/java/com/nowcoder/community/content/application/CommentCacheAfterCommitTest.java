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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CommentCacheAfterCommitTest {

    private static final UUID POST_ID = uuid(8801);
    private static final String COMMENT_BODY = "comment body must not be logged";

    private PostCounterCache postCounterCache;
    private CommentPageCache commentPageCache;
    private CommentCacheAfterCommit cacheAfterCommit;

    @BeforeEach
    void setUp() {
        postCounterCache = mock(PostCounterCache.class);
        commentPageCache = mock(CommentPageCache.class);
        cacheAfterCommit = new CommentCacheAfterCommit(postCounterCache, commentPageCache);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void cacheActionsExecuteOnlyAfterTransactionCommit() {
        beginTransactionSynchronization();

        cacheAfterCommit.incrementCommentCount(POST_ID, 3L);
        cacheAfterCommit.evictCommentPages(POST_ID);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(2);
        verifyNoInteractions(postCounterCache, commentPageCache);

        commitTransactionSynchronization();

        verify(postCounterCache).incrementCommentCount(POST_ID, 3L);
        verify(commentPageCache).evictPost(POST_ID);
    }

    @Test
    void rolledBackTransactionDoesNotUpdateEitherCache() {
        beginTransactionSynchronization();

        cacheAfterCommit.incrementCommentCount(POST_ID, 1L);
        cacheAfterCommit.evictCommentPages(POST_ID);

        rollbackTransactionSynchronization();

        verifyNoInteractions(postCounterCache, commentPageCache);
    }

    @Test
    void counterFailureDoesNotBlockPageEvictionOrLogCommentBody() {
        doThrow(new IllegalStateException(COMMENT_BODY))
                .when(postCounterCache)
                .incrementCommentCount(POST_ID, 1L);
        ListAppender<ILoggingEvent> logs = startLogCapture();
        try {
            beginTransactionSynchronization();
            cacheAfterCommit.incrementCommentCount(POST_ID, 1L);
            cacheAfterCommit.evictCommentPages(POST_ID);

            assertThatCode(this::commitTransactionSynchronization).doesNotThrowAnyException();

            verify(postCounterCache).incrementCommentCount(POST_ID, 1L);
            verify(commentPageCache).evictPost(POST_ID);
            assertWarning(logs, "incrementCommentCount", 1L);
        } finally {
            stopLogCapture(logs);
        }
    }

    @Test
    void pageEvictionFailureDoesNotBlockCounterUpdateOrLogCommentBody() {
        doThrow(new IllegalStateException(COMMENT_BODY)).when(commentPageCache).evictPost(POST_ID);
        ListAppender<ILoggingEvent> logs = startLogCapture();
        try {
            beginTransactionSynchronization();
            cacheAfterCommit.evictCommentPages(POST_ID);
            cacheAfterCommit.incrementCommentCount(POST_ID, -2L);

            assertThatCode(this::commitTransactionSynchronization).doesNotThrowAnyException();

            verify(postCounterCache).incrementCommentCount(POST_ID, -2L);
            verify(commentPageCache).evictPost(POST_ID);
            assertWarning(logs, "evictCommentPages", 0L);
        } finally {
            stopLogCapture(logs);
        }
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

    private ListAppender<ILoggingEvent> startLogCapture() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(CommentCacheAfterCommit.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void stopLogCapture(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(CommentCacheAfterCommit.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private void assertWarning(ListAppender<ILoggingEvent> logs, String operation, long delta) {
        assertThat(logs.list).hasSize(1);
        ILoggingEvent warning = logs.list.get(0);
        assertThat(warning.getLevel()).isEqualTo(Level.WARN);
        assertThat(warning.getFormattedMessage())
                .contains("operation=" + operation)
                .contains("postId=" + POST_ID)
                .contains("delta=" + delta)
                .doesNotContain(COMMENT_BODY);
        assertThat(warning.getArgumentArray()).containsExactly(operation, POST_ID, delta);
        assertThat(warning.getThrowableProxy()).isNull();
    }
}
