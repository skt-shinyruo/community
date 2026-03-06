package com.nowcoder.community.search.event;

import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.PostPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PostProjectionListener {

    private final PostSearchRepository postSearchRepository;

    public PostProjectionListener(PostSearchRepository postSearchRepository) {
        this.postSearchRepository = postSearchRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentLocalEvent event) {
        if (event == null || !(event.payload() instanceof PostPayload payload) || payload.getPostId() <= 0) {
            return;
        }

        if (ContentEventTypes.POST_DELETED.equals(event.type())) {
            postSearchRepository.delete(payload.getPostId());
            return;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) || ContentEventTypes.POST_UPDATED.equals(event.type())) {
            postSearchRepository.upsert(payload);
        }
    }
}
