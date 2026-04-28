package com.nowcoder.community.notice.domain.model;

import java.util.List;

public final class NoticeTopic {

    public static final String COMMENT = "comment";
    public static final String LIKE = "like";
    public static final String FOLLOW = "follow";
    public static final String MODERATION = "moderation";
    public static final List<String> DEFAULT_TOPICS = List.of(COMMENT, LIKE, FOLLOW, MODERATION);

    private NoticeTopic() {
    }
}
