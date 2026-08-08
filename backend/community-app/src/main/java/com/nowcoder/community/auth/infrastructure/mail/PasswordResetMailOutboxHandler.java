package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.auth.application.PasswordResetMailDeliveryApplicationService;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.common.outbox.OutboxTerminalException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PasswordResetMailOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "auth.password-reset-mail";

    private final JsonCodec jsonCodec;
    private final PasswordResetMailDeliveryApplicationService applicationService;

    public PasswordResetMailOutboxHandler(
            JsonCodec jsonCodec,
            PasswordResetMailDeliveryApplicationService applicationService
    ) {
        this.jsonCodec = jsonCodec;
        this.applicationService = applicationService;
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) {
        PasswordResetMailDeliveryApplicationService.DeliveryOutcome outcome =
                applicationService.deliver(decode(event));
        if (outcome == PasswordResetMailDeliveryApplicationService.DeliveryOutcome.EXPIRED) {
            throw new OutboxTerminalException(
                    "delivery_expired", "password reset mail expired before delivery");
        }
        if (outcome == PasswordResetMailDeliveryApplicationService.DeliveryOutcome.INVALID) {
            throw new OutboxTerminalException(
                    "delivery_invalid", "password reset mail delivery metadata is invalid");
        }
    }

    private PasswordResetMailDeliveryApplicationService.Delivery decode(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            throw new OutboxTerminalException(
                    "payload_invalid", "password reset mail outbox payload is missing");
        }
        try {
            return jsonCodec.fromJson(
                    event.payload(),
                    PasswordResetMailDeliveryApplicationService.Delivery.class
            );
        } catch (JsonCodecException exception) {
            throw new OutboxTerminalException(
                    "payload_invalid", "password reset mail outbox payload is invalid");
        }
    }
}
