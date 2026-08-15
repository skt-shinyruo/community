package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationCodeMailDeliveryApplicationServiceTest {

    @Mock
    private RegistrationCodeRepository registrationCodeRepository;

    @Mock
    private MailPort mailPort;

    @Mock
    private RegistrationCodeRepository.DeliveryClaim deliveryClaim;

    private RegistrationCodeMailDeliveryApplicationService service;

    @BeforeEach
    void setUp() {
        RegistrationProperties properties = new RegistrationProperties();
        properties.getCode().setOperationLeaseSeconds(120);
        service = new RegistrationCodeMailDeliveryApplicationService(
                registrationCodeRepository, mailPort, properties, Clock.systemUTC());
    }

    @Test
    void initialDeliveryShouldSendOnlyWhileItsExactCodeStateIsActive() {
        RegistrationCodeMailDispatcher.Delivery stale = initialDelivery(uuid(1), uuid(2));
        when(registrationCodeRepository.claimMailDelivery(
                eq(stale.registrationId()), eq(stale.deliveryId()), eq(stale.code()),
                eq(null), eq(Duration.ofSeconds(120)), eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.empty());

        assertThat(service.deliver(stale))
                .isEqualTo(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.OBSOLETE);

        verifyNoInteractions(mailPort);

        RegistrationCodeMailDispatcher.Delivery current = initialDelivery(uuid(3), stale.registrationId());
        when(registrationCodeRepository.claimMailDelivery(
                eq(current.registrationId()), eq(current.deliveryId()), eq(current.code()),
                eq(null), eq(Duration.ofSeconds(120)), eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.of(deliveryClaim));
        when(deliveryClaim.complete())
                .thenReturn(true);

        assertThat(service.deliver(current))
                .isEqualTo(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.DELIVERED);

        verify(mailPort).sendRegistrationCodeMail(
                "alice@example.com", "123456", deliveryReference(current.deliveryId()));
    }

    @Test
    void replacementDeliveryShouldPrepareSendAndPromoteTheSameLease() {
        UUID deliveryId = uuid(4);
        RegistrationCodeMailDispatcher.Delivery delivery = replacementDelivery(deliveryId, uuid(5));
        when(registrationCodeRepository.claimMailDelivery(
                eq(delivery.registrationId()), eq(deliveryId), eq(delivery.code()),
                eq(deliveryId), eq(Duration.ofSeconds(120)), eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.of(deliveryClaim));
        when(deliveryClaim.complete())
                .thenReturn(true);

        assertThat(service.deliver(delivery))
                .isEqualTo(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.DELIVERED);

        InOrder order = inOrder(registrationCodeRepository, mailPort, deliveryClaim);
        order.verify(registrationCodeRepository).claimMailDelivery(
                eq(delivery.registrationId()), eq(deliveryId), eq(delivery.code()),
                eq(deliveryId), eq(Duration.ofSeconds(120)), eq(Duration.ofSeconds(120)));
        order.verify(mailPort).sendRegistrationCodeMail(
                "alice@example.com", "654321", deliveryReference(deliveryId));
        order.verify(deliveryClaim).complete();
    }

    @Test
    void smtpFailureShouldPropagateForOutboxRetryWithoutPromotion() {
        UUID deliveryId = uuid(6);
        RegistrationCodeMailDispatcher.Delivery delivery = replacementDelivery(deliveryId, uuid(7));
        when(registrationCodeRepository.claimMailDelivery(
                eq(delivery.registrationId()), eq(deliveryId), eq(delivery.code()),
                eq(deliveryId), eq(Duration.ofSeconds(120)), eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.of(deliveryClaim));
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp down"))
                .when(mailPort).sendRegistrationCodeMail(
                        "alice@example.com", "654321", deliveryReference(deliveryId));

        assertThatThrownBy(() -> service.deliver(delivery))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("smtp down");

        verify(deliveryClaim, never()).complete();
    }

    @Test
    void deliveryShouldExpireBeforeSmtpWhenValidityIsShorterThanTheOperationBudget() {
        RegistrationCodeMailDispatcher.Delivery delivery = new RegistrationCodeMailDispatcher.Delivery(
                uuid(80),
                uuid(81),
                null,
                "alice@example.com",
                "123456",
                Instant.now().plusSeconds(90)
        );

        assertThat(service.deliver(delivery))
                .isEqualTo(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.EXPIRED);

        verifyNoInteractions(registrationCodeRepository, mailPort);
    }

    @Test
    void replacementShouldNotReportDeliveredWhenItsStateCannotBeRecoveredAfterSmtp() {
        UUID deliveryId = uuid(82);
        RegistrationCodeMailDispatcher.Delivery delivery = replacementDelivery(deliveryId, uuid(83));
        when(registrationCodeRepository.claimMailDelivery(
                eq(delivery.registrationId()), eq(deliveryId), eq(delivery.code()),
                eq(deliveryId), eq(Duration.ofSeconds(120)), eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.of(deliveryClaim));
        when(deliveryClaim.complete()).thenReturn(false);

        assertThat(service.deliver(delivery))
                .isEqualTo(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.OBSOLETE);

        verify(mailPort).sendRegistrationCodeMail(
                "alice@example.com", "654321", deliveryReference(deliveryId));
    }

    @Test
    void expiredOrMalformedDeliveryShouldBeAcknowledgedWithoutRedisOrSmtp() {
        assertThat(service.deliver(new RegistrationCodeMailDispatcher.Delivery(
                uuid(10), uuid(11), null, "alice@example.com", "123456", Instant.now().minusSeconds(1))))
                .isEqualTo(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.EXPIRED);
        assertThat(service.deliver(new RegistrationCodeMailDispatcher.Delivery(
                uuid(12), uuid(13), uuid(14), "alice@example.com", "123456", Instant.now().plusSeconds(60))))
                .isEqualTo(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.INVALID);

        verifyNoInteractions(registrationCodeRepository, mailPort);
    }

    private static RegistrationCodeMailDispatcher.Delivery initialDelivery(UUID deliveryId, UUID registrationId) {
        return new RegistrationCodeMailDispatcher.Delivery(
                deliveryId, registrationId, null, "alice@example.com", "123456", Instant.now().plusSeconds(600));
    }

    private static RegistrationCodeMailDispatcher.Delivery replacementDelivery(UUID deliveryId, UUID registrationId) {
        return new RegistrationCodeMailDispatcher.Delivery(
                deliveryId, registrationId, deliveryId,
                "alice@example.com", "654321", Instant.now().plusSeconds(600));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static String deliveryReference(UUID deliveryId) {
        return deliveryId.toString().replace("-", "");
    }
}
