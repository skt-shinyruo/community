package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OutboxRegistrationCodeMailDispatcher implements RegistrationCodeMailDispatcher {

    public static final String TOPIC = "auth.registration-code-mail";
    private static final String EVENT_ID_PREFIX = "auth:registration-mail:";

    private final JacksonJsonCodec jsonCodec;
    private final JdbcOutboxEventStore outboxEventStore;

    public OutboxRegistrationCodeMailDispatcher(JacksonJsonCodec jsonCodec, JdbcOutboxEventStore outboxEventStore) {
        this.jsonCodec = jsonCodec;
        this.outboxEventStore = outboxEventStore;
    }

    @Override
    public void dispatch(Delivery delivery) {
        if (delivery == null || delivery.deliveryId() == null || delivery.registrationId() == null
                || delivery.expiresAt() == null || !StringUtils.hasText(delivery.toEmail())
                || !StringUtils.hasText(delivery.code())) {
            throw new IllegalArgumentException("registration mail delivery metadata is incomplete");
        }
        if (delivery.replacementLeaseId() != null
                && !delivery.deliveryId().equals(delivery.replacementLeaseId())) {
            throw new IllegalArgumentException("registration replacement delivery must own its lease");
        }
        try {
            outboxEventStore.enqueue(
                    EVENT_ID_PREFIX + delivery.deliveryId(),
                    TOPIC,
                    delivery.registrationId().toString(),
                    jsonCodec.toJson(delivery)
            );
        } catch (JsonCodecException exception) {
            throw new IllegalStateException("registration mail payload serialization failed", exception);
        }
    }
}
