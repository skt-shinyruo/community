package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.auth.application.PasswordResetMailDeliveryApplicationService;
import com.nowcoder.community.auth.application.port.PasswordResetMailDispatcher;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class OutboxPasswordResetMailDispatcher implements PasswordResetMailDispatcher {

    private static final String EVENT_ID_PREFIX = "auth:pwdreset-mail:";
    private static final String TOPIC = "auth.password-reset-mail";
    private static final Pattern DELIVERY_REFERENCE = Pattern.compile("[A-Za-z0-9_-]{32,128}");

    private final JacksonJsonCodec jsonCodec;
    private final JdbcOutboxEventStore outboxEventStore;

    public OutboxPasswordResetMailDispatcher(JacksonJsonCodec jsonCodec, JdbcOutboxEventStore outboxEventStore) {
        this.jsonCodec = jsonCodec;
        this.outboxEventStore = outboxEventStore;
    }

    @Override
    public void dispatch(
            UUID deliveryId,
            String derivationKeyId,
            String deliveryReference,
            String toEmail,
            Instant expiresAt
    ) {
        if (deliveryId == null
                || expiresAt == null
                || !StringUtils.hasText(derivationKeyId)
                || !StringUtils.hasText(deliveryReference)
                || !DELIVERY_REFERENCE.matcher(deliveryReference.trim()).matches()) {
            throw new IllegalArgumentException("password reset mail delivery metadata is incomplete");
        }
        String eventReference = deliveryReference.trim();
        PasswordResetMailDeliveryApplicationService.Delivery payload =
                new PasswordResetMailDeliveryApplicationService.Delivery(
                        deliveryId,
                        derivationKeyId == null ? "" : derivationKeyId.trim(),
                        eventReference,
                        toEmail == null ? "" : toEmail.trim(),
                        expiresAt
                );
        try {
            outboxEventStore.enqueue(
                    EVENT_ID_PREFIX + eventReference,
                    TOPIC,
                    eventReference,
                    jsonCodec.toJson(payload)
            );
        } catch (JsonCodecException exception) {
            throw new IllegalStateException("password reset mail payload serialization failed", exception);
        }
    }
}
