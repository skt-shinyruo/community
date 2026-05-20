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
        Instant muteUntil,
        Instant banUntil,
        long policyVersion
) {

    public UserAccount(
            UUID id,
            String username,
            String encodedPassword,
            String salt,
            String email,
            int type,
            int status,
            String headerUrl,
            Date createTime,
            Instant muteUntil,
            Instant banUntil
    ) {
        this(id, username, encodedPassword, salt, email, type, status, headerUrl, createTime, muteUntil, banUntil, 0L);
    }
}
