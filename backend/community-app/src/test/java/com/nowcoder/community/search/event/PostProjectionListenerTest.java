package com.nowcoder.community.search.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PostProjectionListenerTest {

    @Test
    void publishedOrUpdatedPostsShouldUpsertIntoSearch() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        PostProjectionListener listener = new PostProjectionListener(repository);
        UUID postId = uuid(100);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);

        listener.onContentEvent(new ContentContractEvent("e1", ContentEventTypes.POST_PUBLISHED, payload));
        listener.onContentEvent(new ContentContractEvent("e2", ContentEventTypes.POST_UPDATED, payload));

        verify(repository, times(2)).upsert(any());
    }

    @Test
    void deletedPostsShouldBeRemovedFromSearch() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        PostProjectionListener listener = new PostProjectionListener(repository);
        UUID postId = uuid(101);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);

        listener.onContentEvent(new ContentContractEvent("e3", ContentEventTypes.POST_DELETED, payload));

        verify(repository).delete(postId);
    }
}
