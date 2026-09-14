package com.nowcoder.community.im.realtime.service;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.realtime.kafka.CommandProducer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.support.SendResult;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Contract: each send attempt emits exactly one terminal {@link CommandIngressResult}
 * through the returned Mono — ack on Kafka success, reject on failure or timeout.
 * Tests observe only the public interface; the ingress signature takes no connection,
 * so a hidden connection side effect is unrepresentable.
 */
class MessageCommandIngressServiceTest {

    @Test
    void sendPrivate_shouldEmitAckTerminalWhenKafkaSendSucceeds() {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        Mockito.when(commandProducer.sendPrivateText(any(SendPrivateTextCommand.class))).thenReturn(future);
        MessageCommandIngressService service = new MessageCommandIngressService(commandProducer);
        AtomicReference<CommandIngressResult> observed = new AtomicReference<>();

        StepVerifier.create(service.sendPrivate(uuid(1), uuid(2), "c1", "hello").doOnNext(observed::set))
                .then(() -> future.complete(null))
                .assertNext(result -> {
                    assertThat(result.acked()).isTrue();
                    assertThat(result.cmd()).isEqualTo("sendPrivateText");
                    assertThat(result.clientMsgId()).isEqualTo("c1");
                    assertThat(result.requestId()).isNotBlank();
                })
                .verifyComplete();

        ArgumentCaptor<SendPrivateTextCommand> command = ArgumentCaptor.forClass(SendPrivateTextCommand.class);
        Mockito.verify(commandProducer).sendPrivateText(command.capture());
        assertThat(command.getValue().fromUserId()).isEqualTo(uuid(1));
        assertThat(observed.get().requestId())
                .as("terminal must correlate with the enqueue attempt")
                .isEqualTo(command.getValue().requestId());
    }

    @Test
    void sendPrivate_shouldEmitRejectTerminalWhenKafkaSendFails() {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        Mockito.when(commandProducer.sendPrivateText(any(SendPrivateTextCommand.class))).thenReturn(future);
        MessageCommandIngressService service = new MessageCommandIngressService(commandProducer);

        StepVerifier.create(service.sendPrivate(uuid(1), uuid(2), "c2", "hello"))
                .then(() -> future.completeExceptionally(new IllegalStateException("broker unavailable")))
                .assertNext(result -> {
                    assertThat(result.acked()).isFalse();
                    assertThat(result.cmd()).isEqualTo("sendPrivateText");
                    assertThat(result.clientMsgId()).isEqualTo("c2");
                    assertThat(result.requestId()).isNotBlank();
                    assertThat(result.code()).isEqualTo(503);
                    assertThat(result.reasonCode()).isEqualTo("kafka_send_failed");
                })
                .verifyComplete();
    }

    @Test
    void sendPrivate_shouldEmitRejectTerminalWhenProducerReturnsNoFuture() {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        Mockito.when(commandProducer.sendPrivateText(any(SendPrivateTextCommand.class))).thenReturn(null);
        MessageCommandIngressService service = new MessageCommandIngressService(commandProducer);

        StepVerifier.create(service.sendPrivate(uuid(1), uuid(2), "c-null", "hello"))
                .assertNext(result -> {
                    assertThat(result.acked()).isFalse();
                    assertThat(result.code()).isEqualTo(503);
                    assertThat(result.reasonCode()).isEqualTo("kafka_send_failed");
                })
                .verifyComplete();
    }

    @Test
    void sendPrivate_shouldEmitExactlyOneTerminalWhenKafkaSendTimesOut() {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        Mockito.when(commandProducer.sendPrivateText(any(SendPrivateTextCommand.class))).thenReturn(future);
        MessageCommandIngressService service = new MessageCommandIngressService(commandProducer, 50L);

        StepVerifier.create(service.sendPrivate(uuid(1), uuid(2), "c3", "hello"))
                .assertNext(result -> {
                    assertThat(result.acked()).isFalse();
                    assertThat(result.cmd()).isEqualTo("sendPrivateText");
                    assertThat(result.clientMsgId()).isEqualTo("c3");
                    assertThat(result.code()).isEqualTo(503);
                    assertThat(result.reasonCode()).isEqualTo("kafka_send_timeout");
                })
                .verifyComplete();

        assertThat(future)
                .as("timeout must release the enqueue future observation, so a late completion cannot emit a second terminal")
                .isCancelled();
    }

    @Test
    void sendPrivate_shouldStopObservingKafkaSendWhenSubscriberCancels() {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        Mockito.when(commandProducer.sendPrivateText(any(SendPrivateTextCommand.class))).thenReturn(future);
        MessageCommandIngressService service = new MessageCommandIngressService(commandProducer, 60_000L);

        StepVerifier.create(service.sendPrivate(uuid(1), uuid(2), "c4", "hello"))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(100L))
                .thenCancel()
                .verify();

        assertThat(future)
                .as("cancellation must release the enqueue future observation")
                .isCancelled();
    }

    @Test
    void sendPrivate_shouldDeferTerminalUntilDownstreamRequests() {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        Mockito.when(commandProducer.sendPrivateText(any(SendPrivateTextCommand.class))).thenReturn(future);
        MessageCommandIngressService service = new MessageCommandIngressService(commandProducer);

        StepVerifier.create(service.sendPrivate(uuid(1), uuid(2), "c6", "hello"), 0)
                .expectSubscription()
                .then(() -> future.complete(null))
                .expectNoEvent(Duration.ofMillis(100L))
                .thenRequest(1)
                .assertNext(result -> {
                    assertThat(result.acked()).isTrue();
                    assertThat(result.clientMsgId()).isEqualTo("c6");
                })
                .verifyComplete();
    }

    @Test
    void sendRoom_shouldEmitAckTerminalWhenKafkaSendSucceeds() {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        Mockito.when(commandProducer.sendRoomText(any(SendRoomTextCommand.class))).thenReturn(future);
        MessageCommandIngressService service = new MessageCommandIngressService(commandProducer);

        StepVerifier.create(service.sendRoom(uuid(1), uuid(9), "c5", "room hello"))
                .then(() -> future.complete(null))
                .assertNext(result -> {
                    assertThat(result.acked()).isTrue();
                    assertThat(result.cmd()).isEqualTo("sendRoomText");
                    assertThat(result.clientMsgId()).isEqualTo("c5");
                    assertThat(result.requestId()).isNotBlank();
                })
                .verifyComplete();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
