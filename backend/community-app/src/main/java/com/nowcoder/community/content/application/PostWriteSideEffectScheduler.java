package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.PostScoreQueue;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostWriteSideEffectScheduler {

    private static final Logger log = LoggerFactory.getLogger(PostWriteSideEffectScheduler.class);

    private final PostScoreQueue postScoreQueue;

    public PostWriteSideEffectScheduler(PostScoreQueue postScoreQueue) {
        this.postScoreQueue = postScoreQueue;
    }

    public void schedulePostScoreRefresh(UUID postId) {
        if (postId == null) {
            return;
        }
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postScoreQueue.add(postId);
            } catch (RuntimeException ex) {
                log.warn("[post-score] enqueue failed after commit (postId={}): {}", postId, ex.toString());
            }
        });
    }
}
