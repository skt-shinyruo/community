package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.event.BestEffortLocalEventListener;
import com.nowcoder.community.content.application.SocialInteractionProjectionApplicationService;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@BestEffortLocalEventListener(reason = "Post score refresh is a derived ranking hint and can be recovered by scheduled recomputation.")
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
