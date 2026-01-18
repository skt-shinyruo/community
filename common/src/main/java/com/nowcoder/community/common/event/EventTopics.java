package com.nowcoder.community.common.event;

public final class EventTopics {

    private EventTopics() {
    }

    public static final String POST_EVENTS_V1 = "community.event.post.v1";
    public static final String COMMENT_EVENTS_V1 = "community.event.comment.v1";
    public static final String SOCIAL_EVENTS_V1 = "community.event.social.v1";

    public static final String DLQ_SUFFIX = ".dlq";
}

