package com.nowcoder.community.user.domain.model;

import java.util.Date;
import java.util.UUID;

public record UserProfile(
        UUID id,
        String username,
        String headerUrl,
        int type,
        int status,
        Date createTime
) {
}
