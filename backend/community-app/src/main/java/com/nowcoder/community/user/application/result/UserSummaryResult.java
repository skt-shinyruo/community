package com.nowcoder.community.user.application.result;

import java.util.UUID;

public record UserSummaryResult(
        UUID id,
        String username,
        String headerUrl,
        int type
) {
}
