package com.nowcoder.community.content.app.post;

import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.infra.tx.AfterCommitExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PostWriteSideEffectScheduler {

    private static final Logger log = LoggerFactory.getLogger(PostWriteSideEffectScheduler.class);

    private final PostScoreQueue postScoreQueue;

    public PostWriteSideEffectScheduler(PostScoreQueue postScoreQueue) {
        this.postScoreQueue = postScoreQueue;
    }

    public void schedulePostScoreRefresh(int postId) {
        if (postId <= 0) {
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
