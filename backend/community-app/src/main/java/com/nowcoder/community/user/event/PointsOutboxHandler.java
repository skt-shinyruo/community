package com.nowcoder.community.user.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import com.nowcoder.community.infra.outbox.OutboxHandler;
import com.nowcoder.community.user.service.PointsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Outbox handler for points projection.
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class PointsOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "projection.points";

    private final ObjectMapper objectMapper;
    private final PointsService pointsService;

    public PointsOutboxHandler(ObjectMapper objectMapper, PointsService pointsService) {
        this.objectMapper = objectMapper;
        this.pointsService = pointsService;
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            return;
        }
        PointsOutboxPayload payload;
        try {
            payload = objectMapper.readValue(event.payload(), PointsOutboxPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("points outbox payload 反序列化失败", e);
        }

        int userId = payload.getUserId();
        int delta = payload.getDelta();
        if (userId <= 0 || delta == 0) {
            return;
        }

        String sourceEventId = payload.getSourceEventId();
        String sourceEventType = payload.getSourceEventType();
        if (!StringUtils.hasText(sourceEventId) || !StringUtils.hasText(sourceEventType)) {
            return;
        }

        pointsService.applyPoints(userId, sourceEventId.trim(), sourceEventType.trim(), delta);
    }

    public static class PointsOutboxPayload {

        private int userId;
        private int delta;
        private String sourceEventId;
        private String sourceEventType;

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public int getDelta() {
            return delta;
        }

        public void setDelta(int delta) {
            this.delta = delta;
        }

        public String getSourceEventId() {
            return sourceEventId;
        }

        public void setSourceEventId(String sourceEventId) {
            this.sourceEventId = sourceEventId;
        }

        public String getSourceEventType() {
            return sourceEventType;
        }

        public void setSourceEventType(String sourceEventType) {
            this.sourceEventType = sourceEventType;
        }
    }
}

