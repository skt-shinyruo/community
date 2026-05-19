package com.nowcoder.community.im.core.kafka;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import com.nowcoder.community.im.core.application.PrivateMessageApplicationService;
import com.nowcoder.community.im.core.application.RoomMessageApplicationService;
import com.nowcoder.community.im.core.outbox.ImMessageOutboxEnqueuer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(OutputCaptureExtension.class)
class CommandConsumersLoggingTest {

    private PrivateMessageApplicationService privateMessageApplicationService;
    private RoomMessageApplicationService roomMessageApplicationService;
    private ImMessageOutboxEnqueuer outboxEnqueuer;
    private CommandConsumers consumers;

    @BeforeEach
    void setUp() {
        privateMessageApplicationService = mock(PrivateMessageApplicationService.class);
        roomMessageApplicationService = mock(RoomMessageApplicationService.class);
        outboxEnqueuer = mock(ImMessageOutboxEnqueuer.class);
        consumers = new CommandConsumers(privateMessageApplicationService, roomMessageApplicationService, outboxEnqueuer);
    }

    @Test
    void privateCommandShouldLogPersistedSummaryAtDebugWithoutMessageContent() {
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        String conversationId = fromUserId + "_" + toUserId;
        SendPrivateTextCommand cmd = new SendPrivateTextCommand(
                "req-1",
                "c1",
                fromUserId,
                toUserId,
                conversationId,
                "hello-private",
                System.currentTimeMillis()
        );
        PrivateMessagePersistedEvent event = new PrivateMessagePersistedEvent(
                "evt-1",
                conversationId,
                7L,
                uuid(7001),
                fromUserId,
                toUserId,
                "hello-private",
                System.currentTimeMillis()
        );
        when(privateMessageApplicationService.persist(cmd)).thenReturn(event);
        CommandConsumersLogCapture capture = startCommandConsumersLogCapture();

        try {
            consumers.onPrivateText(cmd);

            verify(privateMessageApplicationService).persist(cmd);
            verify(outboxEnqueuer, never()).enqueuePrivatePersisted(any(PrivateMessagePersistedEvent.class));
            ILoggingEvent persistedEvent = findSingleEventByAction(capture.appender(), "im_private_command_persist");
            assertThat(persistedEvent.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(persistedEvent.getMDCPropertyMap())
                    .containsEntry(EventLogFields.EVENT_CATEGORY, "async")
                    .containsEntry(EventLogFields.EVENT_ACTION, "im_private_command_persist")
                    .containsEntry(EventLogFields.EVENT_OUTCOME, "success");
            assertThat(persistedEvent.getFormattedMessage())
                    .contains("user.id=" + fromUserId)
                    .contains("community.target_type=conversation")
                    .contains("community.target_id=" + conversationId)
                    .contains("community.message_seq=7")
                    .contains("community.message_id=" + uuid(7001))
                    .contains("community.client_msg_id=c1")
                    .contains("community.request_id=req-1")
                    .doesNotContain("hello-private");
        } finally {
            stopCommandConsumersLogCapture(capture);
        }
    }

    @Test
    void roomCommandShouldLogPersistedSummaryAtDebug() {
        UUID fromUserId = uuid(88);
        SendRoomTextCommand cmd = new SendRoomTextCommand(
                "req-2",
                "c2",
                fromUserId,
                uuid(9001),
                "hello-room",
                System.currentTimeMillis()
        );
        RoomMessagePersistedEvent event = new RoomMessagePersistedEvent(
                "evt-2",
                uuid(9001),
                3L,
                uuid(8080),
                fromUserId,
                System.currentTimeMillis()
        );
        when(roomMessageApplicationService.persist(cmd)).thenReturn(event);
        CommandConsumersLogCapture capture = startCommandConsumersLogCapture();

        try {
            consumers.onRoomText(cmd);

            verify(roomMessageApplicationService).persist(cmd);
            verify(outboxEnqueuer, never()).enqueueRoomPersisted(any(RoomMessagePersistedEvent.class));
            ILoggingEvent persistedEvent = findSingleEventByAction(capture.appender(), "im_room_command_persist");
            assertThat(persistedEvent.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(persistedEvent.getMDCPropertyMap())
                    .containsEntry(EventLogFields.EVENT_CATEGORY, "async")
                    .containsEntry(EventLogFields.EVENT_ACTION, "im_room_command_persist")
                    .containsEntry(EventLogFields.EVENT_OUTCOME, "success");
            assertThat(persistedEvent.getFormattedMessage())
                    .contains("user.id=" + fromUserId)
                    .contains("community.target_type=room")
                    .contains("community.target_id=" + uuid(9001))
                    .contains("community.message_seq=3")
                    .contains("community.message_id=" + uuid(8080))
                    .contains("community.client_msg_id=c2")
                    .contains("community.request_id=req-2")
                    .doesNotContain("hello-room");
        } finally {
            stopCommandConsumersLogCapture(capture);
        }
    }

    @Test
    void roomCommandShouldPublishRejectedEventAndWarnWithoutMessageContent() {
        UUID fromUserId = uuid(88);
        UUID roomId = uuid(9001);
        SendRoomTextCommand cmd = new SendRoomTextCommand(
                "req-2",
                "c2",
                fromUserId,
                roomId,
                "hello-room",
                System.currentTimeMillis()
        );
        when(roomMessageApplicationService.persist(cmd)).thenThrow(new SecurityException("not a room member"));
        CommandConsumersLogCapture capture = startCommandConsumersLogCapture();

        try {
            try {
                consumers.onRoomText(cmd);
            } catch (SecurityException expected) {
            }

            verify(outboxEnqueuer, never()).enqueueRoomPersisted(any(RoomMessagePersistedEvent.class));
            verify(outboxEnqueuer).enqueueRoomRejected(any(RoomMessageRejectedEvent.class));
            ILoggingEvent rejectedEvent = findSingleEventByAction(capture.appender(), "im_room_command_reject");
            assertThat(rejectedEvent.getLevel()).isEqualTo(Level.WARN);
            assertThat(rejectedEvent.getMDCPropertyMap())
                    .containsEntry(EventLogFields.EVENT_CATEGORY, "async")
                    .containsEntry(EventLogFields.EVENT_ACTION, "im_room_command_reject")
                    .containsEntry(EventLogFields.EVENT_OUTCOME, "failure");
            assertThat(rejectedEvent.getFormattedMessage())
                    .contains("user.id=" + fromUserId)
                    .contains("community.target_type=room")
                    .contains("community.target_id=" + roomId)
                    .contains("community.client_msg_id=c2")
                    .contains("community.request_id=req-2")
                    .contains("community.reason_code=command_denied")
                    .contains("community.error_class=java.lang.SecurityException")
                    .contains("community.error_message=not%20a%20room%20member")
                    .doesNotContain("hello-room");
        } finally {
            stopCommandConsumersLogCapture(capture);
        }
    }

    @Test
    void privateCommandShouldNotEnqueueRejectedEventForTransientFailure() {
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        String conversationId = fromUserId + "_" + toUserId;
        SendPrivateTextCommand cmd = new SendPrivateTextCommand(
                "req-transient",
                "c-transient",
                fromUserId,
                toUserId,
                conversationId,
                "hello-private",
                System.currentTimeMillis()
        );
        when(privateMessageApplicationService.persist(cmd)).thenThrow(new IllegalStateException("database unavailable"));

        try {
            consumers.onPrivateText(cmd);
        } catch (IllegalStateException expected) {
        }

        verify(outboxEnqueuer, never()).enqueuePrivatePersisted(any(PrivateMessagePersistedEvent.class));
        verify(outboxEnqueuer, never()).enqueuePrivateRejected(any(PrivateMessageRejectedEvent.class));
    }

    @Test
    void privatePolicyRejectionShouldPublishRejectedEventAndNotThrowForDlq() {
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        String conversationId = fromUserId + "_" + toUserId;
        SendPrivateTextCommand cmd = new SendPrivateTextCommand(
                "req-policy",
                "c-policy",
                fromUserId,
                toUserId,
                conversationId,
                "hello-private",
                System.currentTimeMillis()
        );
        when(privateMessageApplicationService.persist(cmd))
                .thenThrow(new PrivateMessagePolicyVerifier.PrivateMessagePolicyRejectedException(
                        403,
                        "policy_denied",
                        "用户已拉黑"
                ));
        CommandConsumersLogCapture capture = startCommandConsumersLogCapture();

        try {
            consumers.onPrivateText(cmd);

            verify(outboxEnqueuer).enqueuePrivateRejected(argThat(event ->
                    event != null
                            && "req-policy".equals(event.requestId())
                            && "c-policy".equals(event.clientMsgId())
                            && fromUserId.equals(event.fromUserId())
                            && toUserId.equals(event.toUserId())
                            && conversationId.equals(event.conversationId())
                            && event.code() == 403
                            && "policy_denied".equals(event.reasonCode())
                            && "用户已拉黑".equals(event.message())
            ));
            ILoggingEvent rejectedEvent = findSingleEventByAction(capture.appender(), "im_private_command_reject");
            assertThat(rejectedEvent.getFormattedMessage())
                    .contains("community.reason_code=policy_denied")
                    .contains("community.error_message=用户已拉黑")
                    .doesNotContain("hello-private");
        } finally {
            stopCommandConsumersLogCapture(capture);
        }
    }

    @Test
    void defaultErrorHandlerShouldLogDlqRecoverySummary(CapturedOutput output) {
        ListAppender<ILoggingEvent> logs = startKafkaConfigLogCapture();
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.isTransactional()).thenReturn(false);
        when(kafkaTemplate.partitionsFor(ImTopics.COMMAND_ROOM_TEXT + ".dlq"))
                .thenReturn(List.of(new PartitionInfo(ImTopics.COMMAND_ROOM_TEXT + ".dlq", 0, null, null, null)));
        when(kafkaTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        DefaultErrorHandler handler = new KafkaConfig().kafkaDefaultErrorHandler(kafkaTemplate);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(ImTopics.COMMAND_ROOM_TEXT, 0, 15L, "9001", "{\"clientMsgId\":\"c-bad\"}");

        handler.handleOne(
                new IllegalArgumentException("content required"),
                record,
                mock(Consumer.class),
                mock(MessageListenerContainer.class)
        );

        ILoggingEvent recoveryEvent = findSingleEventByAction(logs, "kafka_dlq_recover");
        assertThat(recoveryEvent.getThrowableProxy()).isNull();
        assertThat(recoveryEvent.getMDCPropertyMap())
                .containsEntry(EventLogFields.EVENT_CATEGORY, "async")
                .containsEntry(EventLogFields.EVENT_ACTION, "kafka_dlq_recover")
                .containsEntry(EventLogFields.EVENT_OUTCOME, "degraded");
        assertThat(recoveryEvent.getFormattedMessage())
                .doesNotContain("\n")
                .contains("community.error_class=java.lang.IllegalArgumentException")
                .contains("community.error_message=content%20required");
        assertThat(output.getAll())
                .contains("community.source_topic=" + ImTopics.COMMAND_ROOM_TEXT)
                .contains("community.dlq_topic=" + ImTopics.COMMAND_ROOM_TEXT + ".dlq")
                .contains("community.kafka_partition=0")
                .contains("community.kafka_offset=15")
                .contains("community.reason_code=illegal_argument")
                .doesNotContain("\njava.lang.IllegalArgumentException")
                .doesNotContain("CommandConsumersLoggingTest.defaultErrorHandlerShouldLogDlqRecoverySummary");
        stopKafkaConfigLogCapture(logs);
    }

    private ListAppender<ILoggingEvent> startKafkaConfigLogCapture() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(KafkaConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void stopKafkaConfigLogCapture(ListAppender<ILoggingEvent> appender) {
        if (appender == null) {
            return;
        }
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(KafkaConfig.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private CommandConsumersLogCapture startCommandConsumersLogCapture() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(CommandConsumers.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new CommandConsumersLogCapture(logger, appender, previousLevel);
    }

    private void stopCommandConsumersLogCapture(CommandConsumersLogCapture capture) {
        if (capture == null) {
            return;
        }
        capture.logger().detachAppender(capture.appender());
        capture.appender().stop();
        capture.logger().setLevel(capture.previousLevel());
    }

    private ILoggingEvent findSingleEventByAction(ListAppender<ILoggingEvent> appender, String action) {
        return appender.list.stream()
                .filter(event -> event != null && action.equals(event.getMDCPropertyMap().get(EventLogFields.EVENT_ACTION)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log event found with action=" + action));
    }

    private record CommandConsumersLogCapture(
            Logger logger,
            ListAppender<ILoggingEvent> appender,
            Level previousLevel
    ) {
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
