package com.nowcoder.community.user.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.growth.api.action.GrowthGrantActionApi;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.user.service.PointsProjectionService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final PointsProjectionService pointsProjectionService;

    @Autowired
    public PointsOutboxHandler(ObjectMapper objectMapper, PointsProjectionService pointsProjectionService) {
        this.objectMapper = objectMapper;
        this.pointsProjectionService = pointsProjectionService;
    }

    PointsOutboxHandler(ObjectMapper objectMapper, GrowthGrantActionApi growthGrantActionApi) {
        this(objectMapper, new PointsProjectionService(growthGrantActionApi));
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
        PointsProjectionService.PointsProjectionCommand payload;
        try {
            payload = objectMapper.readValue(event.payload(), PointsProjectionService.PointsProjectionCommand.class);
        } catch (Exception e) {
            throw new IllegalStateException("points outbox payload 反序列化失败", e);
        }
        pointsProjectionService.project(payload);
    }
}
