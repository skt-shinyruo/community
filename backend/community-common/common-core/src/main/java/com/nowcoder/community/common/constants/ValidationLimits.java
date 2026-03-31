package com.nowcoder.community.common.constants;

/**
 * 统一输入校验限额（SSOT）：
 * - 约束公共接口字段长度/列表数量，避免 DoS 与脏数据污染
 * - 作为 @Size 的常量来源，确保跨服务一致
 */
public final class ValidationLimits {

    private ValidationLimits() {
    }

    // 身份相关
    public static final int USERNAME_MAX = 32;
    public static final int PASSWORD_MAX = 64;
    public static final int EMAIL_MAX = 128;
    public static final int CAPTCHA_ID_MAX = 64;
    public static final int CAPTCHA_CODE_MAX = 16;
    public static final int REGISTRATION_EMAIL_CODE_MIN = 6;
    public static final int REGISTRATION_EMAIL_CODE_MAX = 16;

    // 内容相关
    public static final int POST_TITLE_MAX = 128;
    public static final int POST_CONTENT_MAX = 10_000;
    public static final int TAG_MAX = 32;
    public static final int TAGS_MAX = 8;
    public static final int COMMENT_CONTENT_MAX = 2_000;

    // 消息相关
    public static final int MESSAGE_CONTENT_MAX = 2_000;
    public static final int IDS_MAX = 200;
}
