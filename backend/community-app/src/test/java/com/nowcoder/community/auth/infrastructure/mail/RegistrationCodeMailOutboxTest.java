package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.auth.application.RegistrationCodeMailDeliveryApplicationService;
import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxTerminalException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationCodeMailOutboxTest {

    private final JacksonJsonCodec jsonCodec = new JacksonJsonCodec(JacksonJsonCodec.standardMapper());

    @Test
    void dispatcherShouldPersistStableDeliveryMetadata() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxRegistrationCodeMailDispatcher dispatcher =
                new OutboxRegistrationCodeMailDispatcher(jsonCodec, store);
        UUID deliveryId = uuid(1);
        UUID registrationId = uuid(2);
        Instant expiresAt = Instant.now().plusSeconds(600);
        RegistrationCodeMailDispatcher.Delivery delivery = new RegistrationCodeMailDispatcher.Delivery(
                deliveryId, registrationId, deliveryId,
                "alice@example.com", "123456", expiresAt);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

        dispatcher.dispatch(delivery);

        verify(store).enqueue(
                eq("auth:registration-mail:" + deliveryId),
                eq(OutboxRegistrationCodeMailDispatcher.TOPIC),
                eq(registrationId.toString()),
                payload.capture()
        );
        assertThat(payload.getValue())
                .contains(deliveryId.toString(), registrationId.toString(),
                        "alice@example.com", "123456", expiresAt.toString());
    }

    @Test
    void handlerShouldDecodeAndEnterTheRegistrationApplicationBoundary() throws Exception {
        RegistrationCodeMailDeliveryApplicationService applicationService =
                mock(RegistrationCodeMailDeliveryApplicationService.class);
        RegistrationCodeMailOutboxHandler handler =
                new RegistrationCodeMailOutboxHandler(jsonCodec, applicationService);
        RegistrationCodeMailDispatcher.Delivery delivery = new RegistrationCodeMailDispatcher.Delivery(
                uuid(3), uuid(4), null,
                "alice@example.com", "654321", Instant.now().plusSeconds(600));
        when(applicationService.deliver(delivery))
                .thenReturn(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.DELIVERED);

        handler.handle(event(jsonCodec.toJson(delivery)));

        verify(applicationService).deliver(delivery);
        assertThat(handler.topic()).isEqualTo(OutboxRegistrationCodeMailDispatcher.TOPIC);
    }

    @Test
    void handlerShouldMapExpiredDeliveryToTerminalScrubSignal() throws Exception {
        RegistrationCodeMailDeliveryApplicationService applicationService =
                mock(RegistrationCodeMailDeliveryApplicationService.class);
        RegistrationCodeMailOutboxHandler handler =
                new RegistrationCodeMailOutboxHandler(jsonCodec, applicationService);
        RegistrationCodeMailDispatcher.Delivery delivery = new RegistrationCodeMailDispatcher.Delivery(
                uuid(5), uuid(6), null,
                "alice@example.com", "654321", Instant.now().minusSeconds(1));
        when(applicationService.deliver(delivery))
                .thenReturn(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.EXPIRED);

        assertThatThrownBy(() -> handler.handle(event(jsonCodec.toJson(delivery))))
                .isInstanceOf(OutboxTerminalException.class)
                .extracting(error -> ((OutboxTerminalException) error).reasonCode())
                .isEqualTo("delivery_expired");
    }

    private OutboxEvent event(String payload) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "auth:registration-mail:" + UUID.randomUUID(),
                RegistrationCodeMailOutboxHandler.TOPIC,
                "key",
                payload,
                "PROCESSING",
                0,
                Instant.now(),
                null,
                null,
                null
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
