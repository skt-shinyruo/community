package com.nowcoder.community.user.application.result;

import java.util.UUID;

public record UserResolveResult(
        UUID id,
        String username,
        String headerUrl
) {
}
