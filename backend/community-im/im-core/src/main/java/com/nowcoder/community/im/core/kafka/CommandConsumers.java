package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.common.logging.AsyncEventLogger;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.event.ImEventIds;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import com.nowcoder.community.im.core.application.PrivateMessageApplicationService;
import com.nowcoder.community.im.core.application.RoomMessageApplicationService;
import com.nowcoder.community.im.core.outbox.ImMessageOutboxEnqueuer;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
public class CommandConsumers {

    private static final Logger log = LoggerFactory.getLogger(CommandConsumers.class);

    private final PrivateMessageApplicationService privateMessageApplicationService;
    private final RoomMessageApplicationService roomMessageApplicationService;
    private final ImMessageOutboxEnqueuer outboxEnqueuer;

    public CommandConsumers(
            PrivateMessageApplicationService privateMessageApplicationService,
            RoomMessageApplicationService roomMessageApplicationService,
            ImMessageOutboxEnqueuer outboxEnqueuer
    ) {
        this.privateMessageApplicationService = privateMessageApplicationService;
        this.roomMessageApplicationService = roomMessageApplicationService;
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
            var event = privateMessageApplicationService.persist(cmd);
            AsyncEventLogger.debug(
                    log,
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
                AsyncEventLogger.warn(
                        log,
                        "im_private_command_reject",
                        "failure",
                        "user.id", cmd.fromUserId(),
                        "community.target_type", "conversation",
                        "community.target_id", cmd.conversationId(),
                        "community.client_msg_id", cmd.clientMsgId(),
                        "community.request_id", cmd.requestId(),
                        "community.reason_code", rejectionReasonCode(e),
                        "community.error_class", AsyncEventLogger.errorClass(e),
                        "community.error_message", rejectionMessage(e)
                );
                if (e instanceof PrivateMessagePolicyVerifier.PrivateMessagePolicyRejectedException) {
                    return;
                }
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
            var event = roomMessageApplicationService.persist(cmd);
            AsyncEventLogger.debug(
                    log,
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
                AsyncEventLogger.warn(
                        log,
                        "im_room_command_reject",
                        "failure",
                        "user.id", cmd.fromUserId(),
                        "community.target_type", "room",
                        "community.target_id", cmd.roomId(),
                        "community.client_msg_id", cmd.clientMsgId(),
                        "community.request_id", cmd.requestId(),
                        "community.reason_code", rejectionReasonCode(e),
                        "community.error_class", AsyncEventLogger.errorClass(e),
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
        AsyncEventLogger.warn(
                log,
                action,
                "failure",
                "user.id", userId,
                "community.target_type", targetType,
                "community.target_id", targetId,
                "community.client_msg_id", clientMsgId,
                "community.request_id", requestId,
                "community.reason_code", rejectionReasonCode(e),
                "community.error_class", AsyncEventLogger.errorClass(e),
                "community.error_message", rejectionMessage(e)
        );
    }

    private PrivateMessageRejectedEvent toPrivateRejectedEvent(SendPrivateTextCommand cmd, RuntimeException e) {
        return new PrivateMessageRejectedEvent(
                ImEventIds.privateSendResult(cmd.requestId(), cmd.clientMsgId(), cmd.fromUserId()),
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
                ImEventIds.roomSendResult(cmd.requestId(), cmd.clientMsgId(), cmd.fromUserId()),
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
        if (e instanceof PrivateMessagePolicyVerifier.PrivateMessagePolicyRejectedException policyRejection) {
            return policyRejection.code();
        }
        if (e instanceof IllegalArgumentException) {
            return 400;
        }
        if (e instanceof SecurityException) {
            return 403;
        }
        return 503;
    }

    private String rejectionReasonCode(RuntimeException e) {
        if (e instanceof PrivateMessagePolicyVerifier.PrivateMessagePolicyRejectedException policyRejection) {
            return policyRejection.reasonCode();
        }
        if (e instanceof IllegalArgumentException) {
            return "invalid_command";
        }
        if (e instanceof SecurityException) {
            return "command_denied";
        }
        return "command_processing_failed";
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
}
