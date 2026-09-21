package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;

import java.util.Locale;

/**
 * 帖子根评论读取的排序选项。REPLIES 恒定按时间正序，不参与排序选择。
 */
public enum CommentSort {

    LATEST,
    EARLIEST,
    HOT;

    private static final String INVALID_SORT_MESSAGE = "评论排序非法";

    /**
     * 解析排序 query 参数：缺省/空白回落 LATEST，未知取值按参数契约错误拒绝。
     */
    public static CommentSort resolve(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return LATEST;
        }
        try {
            return CommentSort.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, INVALID_SORT_MESSAGE);
        }
    }
}
