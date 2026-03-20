package com.nowcoder.community.common.event;

/**
 * 事件系统的通用约定（与具体业务域无关）。
 */
public final class EventTopicConventions {

    private EventTopicConventions() {
    }

    public static final String DLQ_SUFFIX = ".dlq";
}

