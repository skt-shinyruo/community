package com.nowcoder.community.search.repo;

import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.dto.SearchPostItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPostSearchRepositoryTest {

    @Test
    void searchShouldExposeIndexedUserId() {
        InMemoryPostSearchRepository repository = new InMemoryPostSearchRepository();

        PostPayload payload = new PostPayload();
        payload.setPostId(101);
        payload.setUserId(7);
        payload.setCategoryId(3);
        payload.setTags(List.of("java"));
        payload.setTitle("Search payload");
        payload.setContent("payload content");
        payload.setCreateTime(Instant.parse("2026-03-22T00:00:00Z"));
        payload.setScore(12.5);
        repository.upsert(payload);

        List<SearchPostItem> items = repository.search("payload", null, null, 0, 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPostId()).isEqualTo(101);
        assertThat(items.get(0).getUserId()).isEqualTo(7);
    }
}
