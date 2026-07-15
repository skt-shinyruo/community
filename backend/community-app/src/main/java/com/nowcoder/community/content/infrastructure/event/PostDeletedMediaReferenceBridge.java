package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.PostMediaReferenceSchedulingApplicationService;
import com.nowcoder.community.content.domain.event.PostDeletedDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PostDeletedMediaReferenceBridge {

    private final PostMediaReferenceSchedulingApplicationService applicationService;

    public PostDeletedMediaReferenceBridge(PostMediaReferenceSchedulingApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostDeleted(PostDeletedDomainEvent event) {
        applicationService.scheduleReleaseForDeletedPost(event.postId());
    }
}
