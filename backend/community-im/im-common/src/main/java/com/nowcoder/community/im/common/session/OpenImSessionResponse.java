package com.nowcoder.community.im.common.session;

public record OpenImSessionResponse(
        String sessionId,
        String wsUrl,
        String ticket,
        long expiresAtEpochMillis
) {
}
