package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.content.domain.model.DiscussPost;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class PostHotnessDomainService {

    private static final LocalDateTime EPOCH = LocalDateTime.of(2014, 8, 1, 0, 0);

    public double recomputeScore(DiscussPost post, long likeCount, double signalWeight) {
        if (post == null) {
            return 0.0;
        }
        boolean wonderful = post.getStatus() == 1;
        double weight = (wonderful ? 75.0 : 0.0)
                + Math.max(0, post.getCommentCount()) * 10.0
                + Math.max(0L, likeCount) * 2.0
                + signalWeight;
        double days = 0.0;
        if (post.getCreateTime() != null) {
            days = (post.getCreateTime().getTime() - EPOCH.toInstant(ZoneOffset.UTC).toEpochMilli()) / (1000.0 * 3600 * 24);
        }
        return Math.log10(Math.max(weight, 1.0)) + days;
    }
}
