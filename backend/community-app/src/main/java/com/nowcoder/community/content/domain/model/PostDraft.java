package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record PostDraft(
        UUID userId,
        String title,
        UUID categoryId,
        Date createTime
) {
}
