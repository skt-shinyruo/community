package com.nowcoder.community.user.domain.model;

import java.util.UUID;

public record UserSummary(
        UUID id,
        String username,
        String headerUrl,
        int type
) {
}
