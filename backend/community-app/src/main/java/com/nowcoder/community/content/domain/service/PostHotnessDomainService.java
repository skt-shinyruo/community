package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.content.domain.model.DiscussPost;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class PostHotnessDomainService {

    private static final LocalDateTime EPOCH = LocalDateTime.of(2014, 8, 1, 0, 0);
    private static final double TOP_RANK_OFFSET = 1_000_000.0;

    public double recomputeScore(DiscussPost post, long likeCount) {
        if (post == null) {
            return 0.0;
        }
        double weight = (post.isWonderful() ? 75.0 : 0.0)
                + Math.max(0, post.getCommentCount()) * 10.0
                + Math.max(0L, likeCount) * 2.0;
        double days = 0.0;
        if (post.getCreateTime() != null) {
            days = (post.getCreateTime().getTime() - EPOCH.toInstant(ZoneOffset.UTC).toEpochMilli()) / (1000.0 * 3600 * 24);
        }
        double hotness = Math.log10(Math.max(weight, 1.0)) + days;
        return post.getType() == 1 ? hotness + TOP_RANK_OFFSET : hotness;
    }
}
