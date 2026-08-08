package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.auth.application.RegistrationCodeMailDeliveryApplicationService;
import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.common.outbox.OutboxTerminalException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RegistrationCodeMailOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "auth.registration-code-mail";

    private final JsonCodec jsonCodec;
    private final RegistrationCodeMailDeliveryApplicationService deliveryApplicationService;

    public RegistrationCodeMailOutboxHandler(
            JsonCodec jsonCodec,
            RegistrationCodeMailDeliveryApplicationService deliveryApplicationService
    ) {
        this.jsonCodec = jsonCodec;
        this.deliveryApplicationService = deliveryApplicationService;
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) {
        RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome outcome =
                deliveryApplicationService.deliver(decode(event));
        if (outcome == RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.EXPIRED) {
            throw new OutboxTerminalException(
                    "delivery_expired", "registration code mail expired before delivery");
        }
        if (outcome == RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.INVALID) {
            throw new OutboxTerminalException(
                    "delivery_invalid", "registration code mail delivery metadata is invalid");
        }
    }

    private RegistrationCodeMailDispatcher.Delivery decode(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            throw new OutboxTerminalException(
                    "payload_invalid", "registration mail outbox payload is missing");
        }
        try {
            return jsonCodec.fromJson(event.payload(), RegistrationCodeMailDispatcher.Delivery.class);
        } catch (JsonCodecException exception) {
            throw new OutboxTerminalException(
                    "payload_invalid", "registration mail outbox payload is invalid");
        }
    }
}
