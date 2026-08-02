package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.contracts.event.PostScorePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class PostHotFeedProjectionTransactionOperations {

    private final PostContentRepository postContentRepository;
    private final ContentEventPublisher contentEventPublisher;

    @Autowired
    public PostHotFeedProjectionTransactionOperations(
            PostContentRepository postContentRepository,
            ContentEventPublisher contentEventPublisher
    ) {
        this.postContentRepository = postContentRepository;
        this.contentEventPublisher = contentEventPublisher;
    }

    public PostHotFeedProjectionTransactionOperations(PostContentRepository postContentRepository) {
        this.postContentRepository = postContentRepository;
        this.contentEventPublisher = null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long updateScore(UUID postId, double score, long expectedAggregateVersion) {
        long scoreVersion = postContentRepository.updateScore(postId, score, expectedAggregateVersion);
        if (scoreVersion <= 0L) {
            throw new IllegalStateException("post score version was not advanced: postId=" + postId);
        }
        if (contentEventPublisher != null) {
            contentEventPublisher.publishPostScoreUpdated(new PostScorePayload(
                    postId,
                    expectedAggregateVersion,
                    scoreVersion,
                    score
            ));
        }
        return scoreVersion;
    }
}
