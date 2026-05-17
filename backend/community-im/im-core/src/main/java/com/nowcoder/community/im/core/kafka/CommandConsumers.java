package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import com.nowcoder.community.im.core.outbox.ImMessageOutboxEnqueuer;
import com.nowcoder.community.im.core.service.PrivateMessageService;
import com.nowcoder.community.im.core.service.RoomMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CommandConsumers {

    private static final Logger log = LoggerFactory.getLogger(CommandConsumers.class);
    private static final String CATEGORY_ASYNC = "async";
    private static final String MDC_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_OUTCOME = EventLogFields.EVENT_OUTCOME;

    private final PrivateMessageService privateMessageService;
    private final RoomMessageService roomMessageService;
    private final ImMessageOutboxEnqueuer outboxEnqueuer;

    public CommandConsumers(
            PrivateMessageService privateMessageService,
            RoomMessageService roomMessageService,
            ImMessageOutboxEnqueuer outboxEnqueuer
    ) {
        this.privateMessageService = privateMessageService;
        this.roomMessageService = roomMessageService;
        this.outboxEnqueuer = outboxEnqueuer;
    }

    @KafkaListener(
            topics = "${im.kafka.topics.command-private-text:im.command.private-text}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.command.concurrency:3}"
    )
    public void onPrivateText(SendPrivateTextCommand cmd) {
        if (cmd == null) {
            return;
        }
        try {
            var event = privateMessageService.persist(cmd);
            debugEvent(
                    "im_private_command_persist",
                    "success",
                    "user.id", event.fromUserId(),
                    "community.target_type", "conversation",
                    "community.target_id", event.conversationId(),
                    "community.message_seq", event.seq(),
                    "community.message_id", event.messageId(),
                    "community.client_msg_id", cmd.clientMsgId(),
                    "community.request_id", cmd.requestId()
            );
        } catch (RuntimeException e) {
            if (isBusinessRejection(e)) {
                outboxEnqueuer.enqueuePrivateRejected(toPrivateRejectedEvent(cmd, e));
                warnEvent(
                        "im_private_command_reject",
                        "failure",
                        null,
                        "user.id", cmd.fromUserId(),
                        "community.target_type", "conversation",
                        "community.target_id", cmd.conversationId(),
                        "community.client_msg_id", cmd.clientMsgId(),
                        "community.request_id", cmd.requestId(),
                        "community.reason_code", rejectionReasonCode(e),
                        "community.error_class", errorClass(e),
                        "community.error_message", rejectionMessage(e)
                );
            } else {
                warnProcessingFailure("im_private_command_process", cmd.fromUserId(), "conversation", cmd.conversationId(), cmd.clientMsgId(), cmd.requestId(), e);
            }
            throw e;
        }
    }

    @KafkaListener(
            topics = "${im.kafka.topics.command-room-text:im.command.room-text}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.command.concurrency:3}"
    )
    public void onRoomText(SendRoomTextCommand cmd) {
        if (cmd == null) {
            return;
        }
        try {
            var event = roomMessageService.persist(cmd);
            debugEvent(
                    "im_room_command_persist",
                    "success",
                    "user.id", event.fromUserId(),
                    "community.target_type", "room",
                    "community.target_id", event.roomId(),
                    "community.message_seq", event.seq(),
                    "community.message_id", event.messageId(),
                    "community.client_msg_id", cmd.clientMsgId(),
                    "community.request_id", cmd.requestId()
            );
        } catch (RuntimeException e) {
            if (isBusinessRejection(e)) {
                outboxEnqueuer.enqueueRoomRejected(toRoomRejectedEvent(cmd, e));
                warnEvent(
                        "im_room_command_reject",
                        "failure",
                        null,
                        "user.id", cmd.fromUserId(),
                        "community.target_type", "room",
                        "community.target_id", cmd.roomId(),
                        "community.client_msg_id", cmd.clientMsgId(),
                        "community.request_id", cmd.requestId(),
                        "community.reason_code", rejectionReasonCode(e),
                        "community.error_class", errorClass(e),
                        "community.error_message", rejectionMessage(e)
                );
            } else {
                warnProcessingFailure("im_room_command_process", cmd.fromUserId(), "room", cmd.roomId(), cmd.clientMsgId(), cmd.requestId(), e);
            }
            throw e;
        }
    }

    private boolean isBusinessRejection(RuntimeException e) {
        return e instanceof IllegalArgumentException || e instanceof SecurityException;
    }

    private void warnProcessingFailure(
            String action,
            Object userId,
            String targetType,
            Object targetId,
            String clientMsgId,
            String requestId,
            RuntimeException e
    ) {
        warnEvent(
                action,
                "failure",
                null,
                "user.id", userId,
                "community.target_type", targetType,
                "community.target_id", targetId,
                "community.client_msg_id", clientMsgId,
                "community.request_id", requestId,
                "community.reason_code", rejectionReasonCode(e),
                "community.error_class", errorClass(e),
                "community.error_message", rejectionMessage(e)
        );
    }

    private void debugEvent(String action, String outcome, Object... keyValues) {
        logEvent(action, outcome, false, null, keyValues);
    }

    private void warnEvent(String action, String outcome, Throwable throwable, Object... keyValues) {
        logEvent(action, outcome, true, throwable, keyValues);
    }

    private PrivateMessageRejectedEvent toPrivateRejectedEvent(SendPrivateTextCommand cmd, RuntimeException e) {
        return new PrivateMessageRejectedEvent(
                "evt_reject_" + String.valueOf(cmd.requestId()),
                cmd.requestId(),
                cmd.clientMsgId(),
                cmd.fromUserId(),
                cmd.toUserId(),
                cmd.conversationId(),
                rejectionCode(e),
                rejectionReasonCode(e),
                rejectionMessage(e),
                System.currentTimeMillis()
        );
    }

    private RoomMessageRejectedEvent toRoomRejectedEvent(SendRoomTextCommand cmd, RuntimeException e) {
        return new RoomMessageRejectedEvent(
                "evt_reject_" + String.valueOf(cmd.requestId()),
                cmd.requestId(),
                cmd.clientMsgId(),
                cmd.fromUserId(),
                cmd.roomId(),
                rejectionCode(e),
                rejectionReasonCode(e),
                rejectionMessage(e),
                System.currentTimeMillis()
        );
    }

    private int rejectionCode(RuntimeException e) {
        if (e instanceof IllegalArgumentException) {
            return 400;
        }
        if (e instanceof SecurityException) {
            return 403;
        }
        return 503;
    }

    private String rejectionReasonCode(RuntimeException e) {
        if (e instanceof IllegalArgumentException) {
            return "invalid_command";
        }
        if (e instanceof SecurityException) {
            return "command_denied";
        }
        return "command_processing_failed";
    }

    private String errorClass(Throwable throwable) {
        return throwable == null ? null : throwable.getClass().getName();
    }

    private String rejectionMessage(Throwable throwable) {
        if (throwable == null) {
            return "message processing failed";
        }
        String message = throwable.getMessage();
        if ((throwable instanceof IllegalArgumentException || throwable instanceof SecurityException)
                && message != null && !message.isBlank()) {
            return message;
        }
        return "message processing failed";
    }

    private void logEvent(String action, String outcome, boolean warn, Throwable throwable, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("IM command consumer event keyValues must contain key/value pairs");
        }
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, CATEGORY_ASYNC);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        try {
            String message = buildMessage(action, outcome, keyValues);
            if (warn) {
                if (throwable == null) {
                    log.warn(message);
                } else {
                    log.warn(message, throwable);
                }
                return;
            }
            log.debug(message);
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private String buildMessage(String action, String outcome, Object... keyValues) {
        StringBuilder message = new StringBuilder(160);
        for (int i = 0; i < keyValues.length; i += 2) {
            appendToken(message, String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return message.toString();
    }

    private void appendToken(StringBuilder message, String key, Object value) {
        if (message.length() > 0) {
            message.append(' ');
        }
        message.append(key).append('=').append(encodeTokenValue(value));
    }

    private String encodeTokenValue(Object value) {
        if (value == null) {
            return "-";
        }
        String raw = String.valueOf(value);
        if (raw.isEmpty()) {
            return "-";
        }
        StringBuilder encoded = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '=' || ch == '%') {
                encoded.append('%');
                String hex = Integer.toHexString(ch).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    encoded.append('0');
                }
                encoded.append(hex);
            } else {
                encoded.append(ch);
            }
        }
        return encoded.toString();
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
