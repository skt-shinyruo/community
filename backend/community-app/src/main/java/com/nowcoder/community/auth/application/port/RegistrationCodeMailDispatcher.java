package com.nowcoder.community.auth.application.port;

import java.time.Instant;
import java.util.UUID;

public interface RegistrationCodeMailDispatcher {

    void dispatch(Delivery delivery);

    record Delivery(
            UUID deliveryId,
            UUID registrationId,
            UUID replacementLeaseId,
            String toEmail,
            String code,
            Instant expiresAt
    ) {
    }
}
