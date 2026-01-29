package com.nowcoder.community.common.domain;

/**
 * 跨服务契约：entityType / targetType 的 SSOT。
 *
 * <p>说明：这些值会出现在 DB（评论/点赞/关注/举报等）、Kafka 事件 payload、以及前后端契约中，
 * 必须保证全链路一致且可演进（禁止各模块各自用魔法数字）。</p>
 */
public final class EntityTypes {

    private EntityTypes() {
    }

    public static final int POST = 1;
    public static final int COMMENT = 2;
    public static final int USER = 3;

    public static boolean isValid(int type) {
        return type == POST || type == COMMENT || type == USER;
    }

    public static String nameOf(int type) {
        if (type == POST) {
            return "post";
        }
        if (type == COMMENT) {
            return "comment";
        }
        if (type == USER) {
            return "user";
        }
        return "unknown(" + type + ")";
    }
}

