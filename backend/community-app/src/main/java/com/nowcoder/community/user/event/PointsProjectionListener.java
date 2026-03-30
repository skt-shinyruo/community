package com.nowcoder.community.user.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.growth.api.action.GrowthGrantActionApi;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.user.service.PointsProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PointsProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(PointsProjectionListener.class);

    private final PointsProjectionService pointsProjectionService;

    public PointsProjectionListener(PointsProjectionService pointsProjectionService) {
        this.pointsProjectionService = pointsProjectionService;
    }

    PointsProjectionListener(GrowthGrantActionApi growthGrantActionApi) {
        this(new PointsProjectionService(growthGrantActionApi));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        if (event == null) {
            return;
        }
        try {
            pointsProjectionService.project(pointsProjectionService.commandForContentEvent(event));
        } catch (RuntimeException e) {
            log.warn("[points] projection failed after commit (eventId={}, type={}): {}", event.eventId(), event.type(), e.toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
        try {
            pointsProjectionService.project(pointsProjectionService.commandForSocialEvent(event));
        } catch (RuntimeException e) {
            log.warn("[points] projection failed after commit (eventId={}, type={}): {}", event.eventId(), event.type(), e.toString());
        }
    }
}
