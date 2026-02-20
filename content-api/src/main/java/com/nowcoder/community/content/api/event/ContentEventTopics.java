package com.nowcoder.community.content.api.event;

/**
 * content-service 作为生产方的事件 Topic 定义。
 */
public final class ContentEventTopics {

    private ContentEventTopics() {
    }

    public static final String POST_EVENTS_V1 = "community.event.post.v1";
    public static final String COMMENT_EVENTS_V1 = "community.event.comment.v1";

    /**
     * moderation 事件 Topic：承载 content-service 的处置动作 & user-service 的处罚状态变更。
     *
     * <p>说明：由于该 Topic 属于“跨域一致性协议”，在代码上放在 content-api 统一承载（consumer 按需依赖）。</p>
     */
    public static final String MODERATION_EVENTS_V1 = "community.event.moderation.v1";
}

