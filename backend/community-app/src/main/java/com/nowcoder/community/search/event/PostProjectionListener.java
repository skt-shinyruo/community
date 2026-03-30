package com.nowcoder.community.search.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PostProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(PostProjectionListener.class);

    private final PostSearchRepository postSearchRepository;

    public PostProjectionListener(PostSearchRepository postSearchRepository) {
        this.postSearchRepository = postSearchRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        if (event == null) {
            return;
        }
        if (!(event.payload() instanceof PostPayload payload) || payload.getPostId() <= 0) {
            return;
        }

        try {
            if (ContentEventTypes.POST_DELETED.equals(event.type())) {
                postSearchRepository.delete(payload.getPostId());
                return;
            }
            if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) || ContentEventTypes.POST_UPDATED.equals(event.type())) {
                postSearchRepository.upsert(payload);
            }
        } catch (RuntimeException e) {
            log.warn(
                    "[search] post projection failed after commit (eventId={}, type={}, postId={}): {}",
                    event.eventId(),
                    event.type(),
                    payload.getPostId(),
                    e.toString()
            );
        }
    }
}
