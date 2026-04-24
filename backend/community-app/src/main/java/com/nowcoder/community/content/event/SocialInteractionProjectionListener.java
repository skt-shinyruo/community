package com.nowcoder.community.content.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.service.SocialInteractionProjectionApplicationService;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class SocialInteractionProjectionListener {

    private final SocialInteractionProjectionApplicationService socialInteractionProjectionApplicationService;

    public SocialInteractionProjectionListener(
            SocialInteractionProjectionApplicationService socialInteractionProjectionApplicationService
    ) {
        this.socialInteractionProjectionApplicationService = socialInteractionProjectionApplicationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
        socialInteractionProjectionApplicationService.projectSocialEvent(event);
    }
}
