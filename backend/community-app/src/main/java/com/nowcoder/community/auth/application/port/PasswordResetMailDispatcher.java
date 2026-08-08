package com.nowcoder.community.auth.application.port;

import java.time.Instant;
import java.util.UUID;

public interface PasswordResetMailDispatcher {

    void dispatch(
            UUID deliveryId,
            String derivationKeyId,
            String deliveryReference,
            String toEmail,
            Instant expiresAt
    );
}
