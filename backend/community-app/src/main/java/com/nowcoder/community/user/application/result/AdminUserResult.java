package com.nowcoder.community.user.application.result;

import java.util.Date;
import java.util.UUID;

public record AdminUserResult(
        UUID id,
        String username,
        String email,
        int type,
        int status,
        String headerUrl,
        Date createTime
) {
}
