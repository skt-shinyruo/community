package com.nowcoder.community.content.app.post;

import com.nowcoder.community.content.score.PostScoreQueue;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PostWriteSideEffectSchedulerTest {

    @Test
    void schedulePostScoreRefreshShouldRunImmediatelyWhenNoTransaction() {
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        PostWriteSideEffectScheduler scheduler = new PostWriteSideEffectScheduler(postScoreQueue);

        scheduler.schedulePostScoreRefresh(101);

        verify(postScoreQueue).add(101);
    }

    @Test
    void schedulePostScoreRefreshShouldRunAfterCommitWhenTransactionActive() {
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        PostWriteSideEffectScheduler scheduler = new PostWriteSideEffectScheduler(postScoreQueue);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            scheduler.schedulePostScoreRefresh(101);

            verifyNoInteractions(postScoreQueue);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(postScoreQueue).add(101);
    }
}
