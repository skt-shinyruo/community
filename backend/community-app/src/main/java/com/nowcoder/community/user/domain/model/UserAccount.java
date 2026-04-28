package com.nowcoder.community.user.domain.model;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String username,
        String encodedPassword,
        String salt,
        String email,
        int type,
        int status,
        String headerUrl,
        Date createTime,
        int score,
        Instant muteUntil,
        Instant banUntil
) {
}
