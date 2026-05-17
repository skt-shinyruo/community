package com.nowcoder.community.search.domain.service;

import com.nowcoder.community.search.domain.model.PostSearchQuery;

import java.util.UUID;

public class PostSearchDomainService {

    private static final int DELETED_STATUS = 2;

    public PostSearchQuery normalizeSearchQuery(String keyword, UUID categoryId, String tag, Integer page, Integer size) {
        return normalizeSearchQuery(keyword, categoryId, tag, page, size, 50);
    }

    public PostSearchQuery normalizeSearchQuery(
            String keyword,
            UUID categoryId,
            String tag,
            Integer page,
            Integer size,
            int maxPageSize
    ) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeMaxPageSize = Math.max(1, maxPageSize);
        int safeSize = size == null ? 10 : Math.min(safeMaxPageSize, Math.max(1, size));
        return new PostSearchQuery(normalizeKeyword(keyword), categoryId, normalizeTag(tag), safePage, safeSize);
    }

    public boolean shouldIndex(UUID postId, Integer status) {
        return postId != null && (status == null || status != DELETED_STATUS);
    }

    private String normalizeKeyword(String keyword) {
        return hasText(keyword) ? keyword.trim() : "";
    }

    private String normalizeTag(String tag) {
        String safeTag = hasText(tag) ? tag.trim() : "";
        if (safeTag.startsWith("#")) {
            safeTag = safeTag.substring(1).trim();
        }
        return hasText(safeTag) ? safeTag : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
