package com.nowcoder.community.search.application.command;

import java.util.UUID;

public record SearchPostsCommand(
        String keyword,
        UUID categoryId,
        String tag,
        Integer page,
        Integer size
) {
}
