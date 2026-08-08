package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.config.PasswordResetUrlPolicy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PasswordResetMailDeliveryApplicationService {

    private final MailPort mailPort;
    private final PasswordResetProperties properties;
    private final PasswordResetTokenDeriver tokenDeriver;
    private final Clock clock;

    public PasswordResetMailDeliveryApplicationService(
            MailPort mailPort,
            PasswordResetProperties properties,
            PasswordResetTokenDeriver tokenDeriver,
            Clock clock
    ) {
        this.mailPort = Objects.requireNonNull(mailPort, "mailPort must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.tokenDeriver = Objects.requireNonNull(tokenDeriver, "tokenDeriver must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public DeliveryOutcome deliver(Delivery command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.deliveryId() == null || command.expiresAt() == null) {
            return DeliveryOutcome.INVALID;
        }

        // Unknown/blocked/quota-suppressed accounts intentionally enqueue a hidden no-op.
        if (!StringUtils.hasText(command.toEmail())) {
            return DeliveryOutcome.HIDDEN_NOOP;
        }
        if (!command.expiresAt().isAfter(clock.instant())) {
            return DeliveryOutcome.EXPIRED;
        }
        if (!StringUtils.hasText(command.derivationKeyId())
                || !StringUtils.hasText(command.deliveryReference())) {
            return DeliveryOutcome.INVALID;
        }

        PasswordResetTokenDeriver.DeliveryMaterial material = tokenDeriver.deriveDelivery(
                command.deliveryId(), command.derivationKeyId());
        if (!MessageDigest.isEqual(
                material.deliveryReference().getBytes(StandardCharsets.US_ASCII),
                command.deliveryReference().trim().getBytes(StandardCharsets.US_ASCII))) {
            return DeliveryOutcome.INVALID;
        }

        String baseUrl = PasswordResetUrlPolicy.normalizeHttpsBaseUrl(properties.getResetBaseUrl());
        mailPort.sendPasswordResetMail(
                command.toEmail().trim(),
                baseUrl + "/#/auth/password/reset?token=" + material.token(),
                material.deliveryReference()
        );
        return DeliveryOutcome.DELIVERED;
    }

    public record Delivery(
            UUID deliveryId,
            String derivationKeyId,
            String deliveryReference,
            String toEmail,
            Instant expiresAt
    ) {
    }

    public enum DeliveryOutcome {
        DELIVERED,
        HIDDEN_NOOP,
        EXPIRED,
        INVALID
    }
}
