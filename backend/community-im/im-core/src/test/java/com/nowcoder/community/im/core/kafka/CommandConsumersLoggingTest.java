package com.nowcoder.community.im.core.kafka;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.common.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEventV1;
import com.nowcoder.community.im.core.service.PrivateMessageService;
import com.nowcoder.community.im.core.service.RoomMessageService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class CommandConsumersLoggingTest {

    private PrivateMessageService privateMessageService;
    private RoomMessageService roomMessageService;
    private EventProducer eventProducer;
    private CommandConsumers consumers;

    @BeforeEach
    void setUp() {
        privateMessageService = mock(PrivateMessageService.class);
        roomMessageService = mock(RoomMessageService.class);
        eventProducer = mock(EventProducer.class);
        consumers = new CommandConsumers(privateMessageService, roomMessageService, eventProducer);
    }

    @Test
    void privateCommandShouldLogPersistedSummaryAtDebugWithoutMessageContent() {
        SendPrivateTextCommandV1 cmd = new SendPrivateTextCommandV1(
                "req-1",
                "c1",
                101,
                202,
                "101_202",
                "hello-private",
                System.currentTimeMillis()
        );
        PrivateMessagePersistedEventV1 event = new PrivateMessagePersistedEventV1(
                "evt-1",
                "101_202",
                7L,
                7001L,
                101,
                202,
                "hello-private",
                System.currentTimeMillis()
        );
        when(privateMessageService.persist(cmd)).thenReturn(event);
        CommandConsumersLogCapture capture = startCommandConsumersLogCapture();

        try {
            consumers.onPrivateText(cmd);

            verify(eventProducer).publishPrivatePersisted(event);
            ILoggingEvent persistedEvent = findSingleEvent(capture.appender(), "community.action=im_private_command_persist");
            assertThat(persistedEvent.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(persistedEvent.getFormattedMessage())
                    .contains("community.category=async")
                    .contains("community.action=im_private_command_persist")
                    .contains("community.outcome=success")
                    .contains("user.id=101")
                    .contains("community.target_type=conversation")
                    .contains("community.target_id=101_202")
                    .contains("community.message_seq=7")
                    .contains("community.message_id=7001")
                    .contains("community.client_msg_id=c1")
                    .doesNotContain("hello-private");
        } finally {
            stopCommandConsumersLogCapture(capture);
        }
    }

    @Test
    void roomCommandShouldLogPersistedSummaryAtDebug() {
        SendRoomTextCommandV1 cmd = new SendRoomTextCommandV1(
                "req-2",
                "c2",
                88,
                9001L,
                "hello-room",
                System.currentTimeMillis()
        );
        RoomMessagePersistedEventV1 event = new RoomMessagePersistedEventV1(
                "evt-2",
                9001L,
                3L,
                8080L,
                88,
                System.currentTimeMillis()
        );
        when(roomMessageService.persist(cmd)).thenReturn(event);
        CommandConsumersLogCapture capture = startCommandConsumersLogCapture();

        try {
            consumers.onRoomText(cmd);

            verify(eventProducer).publishRoomPersisted(event);
            ILoggingEvent persistedEvent = findSingleEvent(capture.appender(), "community.action=im_room_command_persist");
            assertThat(persistedEvent.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(persistedEvent.getFormattedMessage())
                    .contains("community.category=async")
                    .contains("community.action=im_room_command_persist")
                    .contains("community.outcome=success")
                    .contains("user.id=88")
                    .contains("community.target_type=room")
                    .contains("community.target_id=9001")
                    .contains("community.message_seq=3")
                    .contains("community.message_id=8080")
                    .contains("community.client_msg_id=c2")
                    .doesNotContain("hello-room");
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
        when(kafkaTemplate.partitionsFor(ImTopics.COMMAND_ROOM_TEXT_V1 + ".dlq"))
                .thenReturn(List.of(new PartitionInfo(ImTopics.COMMAND_ROOM_TEXT_V1 + ".dlq", 0, null, null, null)));
        when(kafkaTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        DefaultErrorHandler handler = new KafkaConfig().kafkaDefaultErrorHandler(kafkaTemplate);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(ImTopics.COMMAND_ROOM_TEXT_V1, 0, 15L, "9001", "{\"clientMsgId\":\"c-bad\"}");

        handler.handleOne(
                new IllegalArgumentException("content required"),
                record,
                mock(Consumer.class),
                mock(MessageListenerContainer.class)
        );

        ILoggingEvent recoveryEvent = findSingleEvent(logs, "community.action=kafka_dlq_recover");
        assertThat(recoveryEvent.getThrowableProxy()).isNull();
        assertThat(recoveryEvent.getFormattedMessage())
                .doesNotContain("\n")
                .contains("community.error_class=java.lang.IllegalArgumentException")
                .contains("community.error_message=content%20required");
        assertThat(output.getAll())
                .contains("community.category=async")
                .contains("community.action=kafka_dlq_recover")
                .contains("community.outcome=degraded")
                .contains("community.source_topic=" + ImTopics.COMMAND_ROOM_TEXT_V1)
                .contains("community.dlq_topic=" + ImTopics.COMMAND_ROOM_TEXT_V1 + ".dlq")
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

    private ILoggingEvent findSingleEvent(ListAppender<ILoggingEvent> appender, String token) {
        return appender.list.stream()
                .filter(event -> event != null && event.getFormattedMessage() != null && event.getFormattedMessage().contains(token))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log event found containing token=" + token));
    }

    private record CommandConsumersLogCapture(
            Logger logger,
            ListAppender<ILoggingEvent> appender,
            Level previousLevel
    ) {
    }
}
