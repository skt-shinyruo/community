package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PasswordResetMailDeliveryApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    private PasswordResetProperties properties;
    private PasswordResetTokenDeriver tokenDeriver;
    private MailPort mailPort;
    private PasswordResetMailDeliveryApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new PasswordResetProperties();
        properties.setResetBaseUrl("https://community.example");
        properties.setIdentifierHmacSecret("current-password-reset-delivery-secret");
        properties.setQuotaHmacSecret("stable-password-reset-quota-secret");
        tokenDeriver = new PasswordResetTokenDeriver(properties);
        mailPort = mock(MailPort.class);
        service = service(properties, tokenDeriver, mailPort);
    }

    @Test
    void deliverShouldRecoverTokenAndPassStableReferenceThroughMailPort() {
        UUID deliveryId = UUID.randomUUID();
        PasswordResetTokenDeriver.DeliveryMaterial material = tokenDeriver.deriveDelivery(deliveryId);

        assertThat(service.deliver(delivery(
                deliveryId, material, "alice@example.com", NOW.plusSeconds(600))))
                .isEqualTo(PasswordResetMailDeliveryApplicationService.DeliveryOutcome.DELIVERED);

        verify(mailPort).sendPasswordResetMail(
                "alice@example.com",
                "https://community.example/#/auth/password/reset?token=" + material.token(),
                material.deliveryReference()
        );
    }

    @Test
    void deliverShouldUseThePayloadKeyAcrossASecretRotation() {
        UUID deliveryId = UUID.randomUUID();
        String previousSecret = properties.getIdentifierHmacSecret();
        PasswordResetTokenDeriver.DeliveryMaterial issued = tokenDeriver.deriveDelivery(deliveryId);
        PasswordResetProperties rotated = new PasswordResetProperties();
        rotated.setResetBaseUrl("https://community.example");
        rotated.setIdentifierHmacSecret("rotated-password-reset-delivery-secret");
        rotated.setPreviousIdentifierHmacSecrets(List.of(previousSecret));
        rotated.setQuotaHmacSecret("stable-password-reset-quota-secret");
        MailPort rotatedMailPort = mock(MailPort.class);

        assertThat(service(rotated, new PasswordResetTokenDeriver(rotated), rotatedMailPort)
                .deliver(delivery(deliveryId, issued, "alice@example.com", NOW.plusSeconds(600))))
                .isEqualTo(PasswordResetMailDeliveryApplicationService.DeliveryOutcome.DELIVERED);

        verify(rotatedMailPort).sendPasswordResetMail(
                "alice@example.com",
                "https://community.example/#/auth/password/reset?token=" + issued.token(),
                issued.deliveryReference()
        );
    }

    @Test
    void deliverShouldPropagateMailFailureForOutboxRetry() {
        UUID deliveryId = UUID.randomUUID();
        PasswordResetTokenDeriver.DeliveryMaterial material = tokenDeriver.deriveDelivery(deliveryId);
        String link = "https://community.example/#/auth/password/reset?token=" + material.token();
        IllegalStateException failure = new IllegalStateException("smtp down");
        doThrow(failure).when(mailPort).sendPasswordResetMail(
                "alice@example.com", link, material.deliveryReference());

        assertThatThrownBy(() -> service.deliver(
                delivery(deliveryId, material, "alice@example.com", NOW.plusSeconds(600))))
                .isSameAs(failure);
    }

    @Test
    void deliverShouldFailExpiredDeliverableEventSoOutboxCanMoveItToDead() {
        UUID deliveryId = UUID.randomUUID();
        PasswordResetTokenDeriver.DeliveryMaterial material = tokenDeriver.deriveDelivery(deliveryId);

        assertThat(service.deliver(delivery(deliveryId, material, "alice@example.com", NOW)))
                .isEqualTo(PasswordResetMailDeliveryApplicationService.DeliveryOutcome.EXPIRED);

        verifyNoInteractions(mailPort);
    }

    @Test
    void deliverShouldAcknowledgeHiddenNoopEvenAfterItsDummyTokenExpires() {
        assertThat(service.deliver(new PasswordResetMailDeliveryApplicationService.Delivery(
                UUID.randomUUID(), "", "", "", NOW.minusSeconds(1))))
                .isEqualTo(PasswordResetMailDeliveryApplicationService.DeliveryOutcome.HIDDEN_NOOP);

        verifyNoInteractions(mailPort);
    }

    @Test
    void deliverShouldRejectTamperedDeliveryReference() {
        UUID deliveryId = UUID.randomUUID();
        PasswordResetTokenDeriver.DeliveryMaterial material = tokenDeriver.deriveDelivery(deliveryId);
        PasswordResetMailDeliveryApplicationService.Delivery tampered =
                new PasswordResetMailDeliveryApplicationService.Delivery(
                        deliveryId,
                        material.derivationKeyId(),
                        "A".repeat(43),
                        "alice@example.com",
                        NOW.plusSeconds(600)
                );

        assertThat(service.deliver(tampered))
                .isEqualTo(PasswordResetMailDeliveryApplicationService.DeliveryOutcome.INVALID);
        verify(mailPort, never()).sendPasswordResetMail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private PasswordResetMailDeliveryApplicationService service(
            PasswordResetProperties configuredProperties,
            PasswordResetTokenDeriver configuredDeriver,
            MailPort configuredMailPort
    ) {
        return new PasswordResetMailDeliveryApplicationService(
                configuredMailPort,
                configuredProperties,
                configuredDeriver,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private PasswordResetMailDeliveryApplicationService.Delivery delivery(
            UUID deliveryId,
            PasswordResetTokenDeriver.DeliveryMaterial material,
            String toEmail,
            Instant expiresAt
    ) {
        return new PasswordResetMailDeliveryApplicationService.Delivery(
                deliveryId,
                material.derivationKeyId(),
                material.deliveryReference(),
                toEmail,
                expiresAt
        );
    }
}
