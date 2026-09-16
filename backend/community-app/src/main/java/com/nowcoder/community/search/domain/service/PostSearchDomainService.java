package com.nowcoder.community.search.domain.service;

import com.nowcoder.community.search.domain.model.PostSearchQuery;

import java.util.UUID;

public class PostSearchDomainService {

    private static final int DELETED_STATUS = 2;

    public PostSearchQuery normalizeSearchQuery(String keyword, UUID categoryId, String tag, Integer page, Integer size, int maxPageSize) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 10 : Math.min(maxPageSize, Math.max(1, size));
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

    // ponytail: domain 层禁用 Spring（ArchUnit 红线），保留 3 行纯 Java 判断
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
