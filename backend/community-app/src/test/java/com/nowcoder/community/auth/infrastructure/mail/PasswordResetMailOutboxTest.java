package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.auth.application.PasswordResetMailDeliveryApplicationService;
import com.nowcoder.community.auth.application.PasswordResetTokenDeriver;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.common.outbox.OutboxProperties;
import com.nowcoder.community.common.outbox.OutboxTerminalException;
import com.nowcoder.community.common.outbox.OutboxWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordResetMailOutboxTest {

    private JacksonJsonCodec jsonCodec;
    private PasswordResetProperties properties;
    private PasswordResetTokenDeriver tokenDeriver;

    @BeforeEach
    void setUp() {
        jsonCodec = new JacksonJsonCodec(JacksonJsonCodec.standardMapper());
        properties = new PasswordResetProperties();
        properties.setResetBaseUrl("https://community.example");
        properties.setIdentifierHmacSecret("test-password-reset-hmac-secret");
        properties.setQuotaHmacSecret("stable-password-reset-quota-secret");
        tokenDeriver = new PasswordResetTokenDeriver(properties);
    }

    @Test
    void dispatcherShouldUseOpaqueReferenceForPersistentEventIdentity() throws Exception {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxPasswordResetMailDispatcher dispatcher = new OutboxPasswordResetMailDispatcher(jsonCodec, store);
        UUID deliveryId = UUID.randomUUID();
        PasswordResetTokenDeriver.DeliveryMaterial material = tokenDeriver.deriveDelivery(deliveryId);
        Instant expiresAt = Instant.now().plusSeconds(600);
        ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

        dispatcher.dispatch(
                deliveryId,
                material.derivationKeyId(),
                material.deliveryReference(),
                "alice@example.com",
                expiresAt
        );

        verify(store).enqueue(
                eventId.capture(),
                org.mockito.ArgumentMatchers.eq(PasswordResetMailOutboxHandler.TOPIC),
                eventKey.capture(),
                payload.capture()
        );
        assertThat(eventId.getValue())
                .isEqualTo("auth:pwdreset-mail:" + material.deliveryReference())
                .doesNotContain(deliveryId.toString());
        assertThat(eventKey.getValue())
                .isEqualTo(material.deliveryReference())
                .doesNotContain(deliveryId.toString());
        PasswordResetMailDeliveryApplicationService.Delivery persisted = jsonCodec.fromJson(
                payload.getValue(), PasswordResetMailDeliveryApplicationService.Delivery.class);
        assertThat(persisted).isEqualTo(new PasswordResetMailDeliveryApplicationService.Delivery(
                deliveryId,
                material.derivationKeyId(),
                material.deliveryReference(),
                "alice@example.com",
                expiresAt
        ));
        assertThat(payload.getValue()).doesNotContain(material.token());
    }

    @Test
    void dispatcherShouldRejectInvalidIdentityBeforeWritingOutbox() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxPasswordResetMailDispatcher dispatcher = new OutboxPasswordResetMailDispatcher(jsonCodec, store);

        assertThatThrownBy(() -> dispatcher.dispatch(
                UUID.randomUUID(),
                "",
                "not-an-opaque-reference",
                "alice@example.com",
                Instant.now().plusSeconds(600)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("password reset mail delivery metadata is incomplete");

        verifyNoInteractions(store);
    }

    @Test
    void handlerShouldOnlyDecodeAndEnterTheApplicationBoundary() throws Exception {
        PasswordResetMailDeliveryApplicationService applicationService =
                mock(PasswordResetMailDeliveryApplicationService.class);
        PasswordResetMailOutboxHandler handler = new PasswordResetMailOutboxHandler(jsonCodec, applicationService);
        UUID deliveryId = UUID.randomUUID();
        PasswordResetTokenDeriver.DeliveryMaterial material = tokenDeriver.deriveDelivery(deliveryId);
        PasswordResetMailDeliveryApplicationService.Delivery delivery =
                new PasswordResetMailDeliveryApplicationService.Delivery(
                        deliveryId,
                        material.derivationKeyId(),
                        material.deliveryReference(),
                        "alice@example.com",
                        Instant.now().plusSeconds(600)
                );
        when(applicationService.deliver(delivery))
                .thenReturn(PasswordResetMailDeliveryApplicationService.DeliveryOutcome.DELIVERED);

        handler.handle(event(jsonCodec.toJson(delivery)));

        verify(applicationService).deliver(delivery);
    }

    @Test
    void handlerShouldPropagateApplicationFailureForOutboxRetry() throws Exception {
        PasswordResetMailDeliveryApplicationService applicationService =
                mock(PasswordResetMailDeliveryApplicationService.class);
        PasswordResetMailOutboxHandler handler = new PasswordResetMailOutboxHandler(jsonCodec, applicationService);
        PasswordResetMailDeliveryApplicationService.Delivery delivery =
                new PasswordResetMailDeliveryApplicationService.Delivery(
                        UUID.randomUUID(), "key", "reference", "alice@example.com", Instant.now().plusSeconds(600));
        IllegalStateException failure = new IllegalStateException("smtp down");
        doThrow(failure).when(applicationService).deliver(delivery);

        assertThatThrownBy(() -> handler.handle(event(jsonCodec.toJson(delivery))))
                .isSameAs(failure);
    }

    @Test
    void handlerShouldRejectMissingPayloadBeforeEnteringApplicationBoundary() {
        PasswordResetMailDeliveryApplicationService applicationService =
                mock(PasswordResetMailDeliveryApplicationService.class);
        PasswordResetMailOutboxHandler handler = new PasswordResetMailOutboxHandler(jsonCodec, applicationService);

        assertThatThrownBy(() -> handler.handle(event("")))
                .isInstanceOf(OutboxTerminalException.class)
                .hasMessageContaining("payload is missing");
    }

    @Test
    void handlerShouldMapExpiredDeliverableToTerminalScrubSignal() throws Exception {
        PasswordResetMailDeliveryApplicationService applicationService =
                mock(PasswordResetMailDeliveryApplicationService.class);
        PasswordResetMailOutboxHandler handler = new PasswordResetMailOutboxHandler(jsonCodec, applicationService);
        PasswordResetMailDeliveryApplicationService.Delivery delivery =
                new PasswordResetMailDeliveryApplicationService.Delivery(
                        UUID.randomUUID(), "key", "reference", "alice@example.com", Instant.now().minusSeconds(1));
        when(applicationService.deliver(delivery))
                .thenReturn(PasswordResetMailDeliveryApplicationService.DeliveryOutcome.EXPIRED);

        assertThatThrownBy(() -> handler.handle(event(jsonCodec.toJson(delivery))))
                .isInstanceOf(OutboxTerminalException.class)
                .extracting(error -> ((OutboxTerminalException) error).reasonCode())
                .isEqualTo("delivery_expired");
    }

    @Test
    void expiredDeliverableShouldBecomeDeadAndScrubPayloadThroughWorker() {
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
            createSchema(jdbcTemplate);
            JdbcOutboxEventStore store = new JdbcOutboxEventStore(jdbcTemplate);
            OutboxPasswordResetMailDispatcher dispatcher =
                    new OutboxPasswordResetMailDispatcher(jsonCodec, store);
            UUID deliveryId = UUID.randomUUID();
            PasswordResetTokenDeriver.DeliveryMaterial material = tokenDeriver.deriveDelivery(deliveryId);
            String eventId = "auth:pwdreset-mail:" + material.deliveryReference();
            dispatcher.dispatch(
                    deliveryId,
                    material.derivationKeyId(),
                    material.deliveryReference(),
                    "alice@example.com",
                    now
            );
            assertThat(jdbcTemplate.queryForObject(
                    "select payload from outbox_event where event_id = ?",
                    String.class,
                    eventId
            )).contains("alice@example.com");

            MailPort mailPort = mock(MailPort.class);
            PasswordResetMailDeliveryApplicationService deliveryService =
                    new PasswordResetMailDeliveryApplicationService(
                            mailPort,
                            properties,
                            tokenDeriver,
                            Clock.fixed(now, ZoneOffset.UTC)
                    );
            PasswordResetMailOutboxHandler handler =
                    new PasswordResetMailOutboxHandler(jsonCodec, deliveryService);
            OutboxProperties outboxProperties = new OutboxProperties();
            outboxProperties.setEnabled(true);
            outboxProperties.setProcessingLease(Duration.ofSeconds(30));

            try (OutboxWorker worker = new OutboxWorker(
                    store,
                    Map.of(handler.topic(), handler),
                    outboxProperties,
                    Clock.fixed(now, ZoneOffset.UTC)
            )) {
                assertThat(worker.pollOnce()).isEqualTo(1);
            }

            assertThat(jdbcTemplate.queryForObject(
                    "select status from outbox_event where event_id = ?",
                    String.class,
                    eventId
            )).isEqualTo(OutboxEventStatus.DEAD);
            assertThat(jdbcTemplate.queryForObject(
                    "select payload from outbox_event where event_id = ?",
                    String.class,
                    eventId
            )).isEmpty();
            assertThat(jdbcTemplate.queryForObject(
                    "select last_error from outbox_event where event_id = ?",
                    String.class,
                    eventId
            )).contains("expired before delivery");
            verifyNoInteractions(mailPort);
        } finally {
            database.shutdown();
        }
    }

    private OutboxEvent event(String payload) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "auth:pwdreset-mail:opaque-reference",
                PasswordResetMailOutboxHandler.TOPIC,
                "opaque-reference",
                payload,
                "PROCESSING",
                0,
                Instant.now(),
                null,
                null,
                null
        );
    }

    private void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                create table outbox_event (
                  id binary(16) primary key,
                  event_id varchar(64) not null unique,
                  topic varchar(255) not null,
                  event_key varchar(255) not null,
                  payload clob not null,
                  status varchar(32) not null,
                  lease_token binary(16),
                  processing_lease_until timestamp,
                  retry_count int not null default 0,
                  next_retry_at timestamp,
                  last_error varchar(512),
                  trace_id varchar(32),
                  traceparent varchar(128),
                  created_at timestamp default current_timestamp,
                  updated_at timestamp default current_timestamp
                )
                """);
    }
}
