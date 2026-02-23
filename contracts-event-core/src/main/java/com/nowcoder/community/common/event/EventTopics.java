package com.nowcoder.community.common.event;

/**
 * 社区项目的 Kafka Topic 定义（v1）。
 *
 * <p>说明：Topic 属于跨服务稳定契约，需放在中立的 contracts 层，避免由某个业务域模块“代持”导致反向依赖。</p>
 */
public final class EventTopics {

    private EventTopics() {
    }

    public static final String POST_EVENTS_V1 = "community.event.post.v1";
    public static final String COMMENT_EVENTS_V1 = "community.event.comment.v1";
    public static final String SOCIAL_EVENTS_V1 = "community.event.social.v1";
    public static final String MODERATION_EVENTS_V1 = "community.event.moderation.v1";
}

