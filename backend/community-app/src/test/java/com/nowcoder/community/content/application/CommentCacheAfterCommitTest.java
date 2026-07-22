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
        LogCapture logs = startLogCapture();
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
        LogCapture logs = startLogCapture();
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

    @Test
    void expectedCacheFailureWarningDoesNotPropagateBeyondLocalCapture() {
        doThrow(new IllegalStateException(COMMENT_BODY))
                .when(postCounterCache)
                .incrementCommentCount(POST_ID, 1L);
        Logger rootLogger = (Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> rootEvents = new ListAppender<>();
        rootEvents.start();
        rootLogger.addAppender(rootEvents);
        LogCapture logs = startLogCapture();
        try {
            beginTransactionSynchronization();
            cacheAfterCommit.incrementCommentCount(POST_ID, 1L);

            assertThatCode(this::commitTransactionSynchronization).doesNotThrowAnyException();

            assertWarning(logs, "incrementCommentCount", 1L);
            assertThat(rootEvents.list).isEmpty();
        } finally {
            stopLogCapture(logs);
            rootLogger.detachAppender(rootEvents);
            rootEvents.stop();
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

    private LogCapture startLogCapture() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(CommentCacheAfterCommit.class);
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
            try {
                logs.logger().setAdditive(logs.wasAdditive());
            } finally {
                logs.appender().stop();
            }
        }
    }

    private void assertWarning(LogCapture logs, String operation, long delta) {
        assertThat(logs.appender().list).hasSize(1);
        ILoggingEvent warning = logs.appender().list.get(0);
        assertThat(warning.getLevel()).isEqualTo(Level.WARN);
        assertThat(warning.getFormattedMessage())
                .contains("operation=" + operation)
                .contains("postId=" + POST_ID)
                .contains("delta=" + delta)
                .doesNotContain(COMMENT_BODY);
        assertThat(warning.getArgumentArray()).containsExactly(operation, POST_ID, delta);
        assertThat(warning.getThrowableProxy()).isNull();
    }

    private record LogCapture(Logger logger, ListAppender<ILoggingEvent> appender, boolean wasAdditive) {
    }

}
