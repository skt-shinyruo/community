package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.constants.ValidationLimits;
import com.nowcoder.community.common.exception.BusinessException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class PostContentBlockPolicy {

    public interface ContentBlockCommand<T extends ContentBlockCommand<T>> {

        String type();

        String text();

        UUID assetId();

        String language();

        String caption();

        String displayName();

        Map<String, Object> metadata();

        T normalized(
                String type,
                String text,
                UUID assetId,
                String language,
                String caption,
                String displayName,
                Map<String, Object> metadata
        );
    }

    public List<?> validateAndNormalize(Collection<?> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "帖子内容不能为空");
        }
        throw new BusinessException(INVALID_ARGUMENT, "内容块非法");
    }

    public <T extends ContentBlockCommand<T>> List<T> validateAndNormalize(List<T> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "帖子内容不能为空");
        }
        if (blocks.size() > ValidationLimits.POST_CONTENT_BLOCKS_MAX) {
            throw new BusinessException(INVALID_ARGUMENT, "内容块数量过多");
        }
        return blocks.stream()
                .map(this::normalizeOne)
                .toList();
    }

    private <T extends ContentBlockCommand<T>> T normalizeOne(T raw) {
        if (raw == null) {
            throw new BusinessException(INVALID_ARGUMENT, "内容块非法");
        }
        String type = safe(raw.type()).toLowerCase();
        return switch (type) {
            case "paragraph", "code" -> normalizeTextBlock(type, raw);
            case "image", "video", "file" -> normalizeMediaBlock(type, raw);
            default -> throw new BusinessException(INVALID_ARGUMENT, "不支持的内容块类型");
        };
    }

    private <T extends ContentBlockCommand<T>> T normalizeTextBlock(String type, T raw) {
        String text = limit(safe(raw.text()), ValidationLimits.POST_BLOCK_TEXT_MAX, "文本块过长");
        if (text.isBlank()) {
            throw new BusinessException(INVALID_ARGUMENT, "文本块不能为空");
        }
        String language = "code".equals(type)
                ? limit(safe(raw.language()), ValidationLimits.POST_BLOCK_LANGUAGE_MAX, "代码语言过长")
                : "";
        return raw.normalized(type, text, null, language, "", "", raw.metadata());
    }

    private <T extends ContentBlockCommand<T>> T normalizeMediaBlock(String type, T raw) {
        if (raw.assetId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体块缺少资源");
        }
        String caption = limit(safe(raw.caption()), ValidationLimits.POST_BLOCK_CAPTION_MAX, "说明文字过长");
        String displayName = limit(safe(raw.displayName()), ValidationLimits.POST_BLOCK_DISPLAY_NAME_MAX, "文件名过长");
        return raw.normalized(type, "", raw.assetId(), "", caption, displayName, raw.metadata());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String limit(String value, int max, String message) {
        if (value.length() > max) {
            throw new BusinessException(INVALID_ARGUMENT, message);
        }
        return value;
    }
}
