package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class RegistrationCodeMailDeliveryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationCodeMailDeliveryApplicationService.class);
    private final RegistrationCodeRepository registrationCodeRepository;
    private final MailPort mailPort;
    private final RegistrationProperties properties;
    private final Clock clock;

    public RegistrationCodeMailDeliveryApplicationService(
            RegistrationCodeRepository registrationCodeRepository,
            MailPort mailPort,
            RegistrationProperties properties,
            Clock clock
    ) {
        this.registrationCodeRepository = Objects.requireNonNull(
                registrationCodeRepository, "registrationCodeRepository must not be null");
        this.mailPort = Objects.requireNonNull(mailPort, "mailPort must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public DeliveryOutcome deliver(RegistrationCodeMailDispatcher.Delivery command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!valid(command)) {
            return DeliveryOutcome.INVALID;
        }
        Instant now = clock.instant();
        Duration validityMargin = operationLeaseTtl();
        if (!command.expiresAt().isAfter(now.plus(validityMargin))) {
            return DeliveryOutcome.EXPIRED;
        }

        Instant leaseExpiresAt = now.plus(operationLeaseTtl());
        if (!registrationCodeRepository.prepareMailDelivery(
                command.registrationId(),
                command.deliveryId(),
                command.code(),
                command.replacementLeaseId(),
                leaseExpiresAt,
                validityMargin)) {
            return DeliveryOutcome.OBSOLETE;
        }

        mailPort.sendRegistrationCodeMail(
                command.toEmail().trim(),
                command.code().trim(),
                deliveryReference(command.deliveryId())
        );
        if (command.replacementLeaseId() == null) {
            if (registrationCodeRepository.completeInitialDelivery(
                    command.registrationId(),
                    command.deliveryId(),
                    command.code(),
                    validityMargin)) {
                return DeliveryOutcome.DELIVERED;
            }
            boolean recovered = registrationCodeRepository.prepareMailDelivery(
                    command.registrationId(),
                    command.deliveryId(),
                    command.code(),
                    null,
                    clock.instant().plus(operationLeaseTtl()),
                    validityMargin
            ) && registrationCodeRepository.completeInitialDelivery(
                    command.registrationId(),
                    command.deliveryId(),
                    command.code(),
                    validityMargin);
            if (!recovered) {
                log.warn("[registration] delivered initial code lost its state lease; registrationId={}, deliveryId={}",
                        command.registrationId(), command.deliveryId());
            }
            return recovered ? DeliveryOutcome.DELIVERED : DeliveryOutcome.OBSOLETE;
        }

        if (registrationCodeRepository.promoteReplacement(
                command.registrationId(), command.replacementLeaseId(), validityMargin)) {
            return DeliveryOutcome.DELIVERED;
        }

        Instant recoveryLeaseExpiresAt = clock.instant().plus(operationLeaseTtl());
        boolean recovered = registrationCodeRepository.prepareMailDelivery(
                command.registrationId(),
                command.deliveryId(),
                command.code(),
                command.replacementLeaseId(),
                recoveryLeaseExpiresAt,
                validityMargin
        ) && registrationCodeRepository.promoteReplacement(
                command.registrationId(), command.replacementLeaseId(), validityMargin);
        if (!recovered) {
            log.warn("[registration] delivered replacement code lost its state lease; registrationId={}, deliveryId={}",
                    command.registrationId(), command.deliveryId());
        }
        return recovered ? DeliveryOutcome.DELIVERED : DeliveryOutcome.OBSOLETE;
    }

    private boolean valid(RegistrationCodeMailDispatcher.Delivery command) {
        if (command.deliveryId() == null || command.registrationId() == null
                || command.expiresAt() == null || !StringUtils.hasText(command.toEmail())
                || !StringUtils.hasText(command.code())) {
            return false;
        }
        return command.replacementLeaseId() == null
                || command.deliveryId().equals(command.replacementLeaseId());
    }

    private Duration operationLeaseTtl() {
        return Duration.ofSeconds(Math.max(60, properties.getCode().getOperationLeaseSeconds()));
    }

    private String deliveryReference(java.util.UUID deliveryId) {
        return deliveryId.toString().replace("-", "");
    }

    public enum DeliveryOutcome {
        DELIVERED,
        OBSOLETE,
        EXPIRED,
        INVALID
    }
}
