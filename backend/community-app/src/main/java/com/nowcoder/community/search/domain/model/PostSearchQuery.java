package com.nowcoder.community.search.domain.model;

import java.util.UUID;

public record PostSearchQuery(
        String keyword,
        UUID categoryId,
        String tag,
        int page,
        int size
) {
}
