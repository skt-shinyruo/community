package com.nowcoder.community.search.repo;

import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.dto.SearchPostItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPostSearchRepositoryTest {

    @Test
    void searchShouldExposeIndexedUserId() {
        InMemoryPostSearchRepository repository = new InMemoryPostSearchRepository();
        UUID postId = uuid(101);
        UUID userId = uuid(7);
        UUID categoryId = uuid(3);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        payload.setCategoryId(categoryId);
        payload.setTags(List.of("java"));
        payload.setTitle("Search payload");
        payload.setContent("payload content");
        payload.setCreateTime(Instant.parse("2026-03-22T00:00:00Z"));
        payload.setScore(12.5);
        repository.upsert(payload);

        List<SearchPostItem> items = repository.search("payload", null, null, 0, 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPostId()).isEqualTo(postId);
        assertThat(items.get(0).getUserId()).isEqualTo(userId);
    }
}
