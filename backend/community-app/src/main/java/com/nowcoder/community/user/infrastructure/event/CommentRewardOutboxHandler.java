package com.nowcoder.community.user.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.user.application.UserRewardApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class CommentRewardOutboxHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final UserRewardApplicationService applicationService;
    private final String topic;

    public CommentRewardOutboxHandler(
            ObjectMapper objectMapper,
            UserRewardApplicationService applicationService,
            @Value("${user.reward.outbox.comment-topic:projection.user.reward.comment}") String topic
    ) {
        this.objectMapper = objectMapper;
        this.applicationService = applicationService;
        this.topic = topic;
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            return;
        }

        CommentPayload payload;
        try {
            payload = objectMapper.readValue(event.payload(), CommentPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("user reward comment outbox payload 反序列化失败", e);
        }

        if (payload.getCommentId() == null || payload.getUserId() == null) {
            return;
        }
        applicationService.apply(applicationService.commandForCommentCreated(
                payload.getCommentId(),
                payload.getUserId()
        ));
    }
}
