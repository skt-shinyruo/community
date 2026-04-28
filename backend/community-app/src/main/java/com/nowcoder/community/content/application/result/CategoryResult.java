package com.nowcoder.community.content.application.result;

import java.util.UUID;

public record CategoryResult(
        UUID id,
        String name,
        String description,
        int position,
        int postCount
) {
}
