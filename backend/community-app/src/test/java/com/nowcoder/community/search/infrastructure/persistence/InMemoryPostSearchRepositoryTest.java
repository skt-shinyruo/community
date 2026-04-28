package com.nowcoder.community.search.infrastructure.persistence;

import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchHit;
import com.nowcoder.community.search.domain.model.PostSearchQuery;
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

        PostSearchDocument document = new PostSearchDocument(
                postId,
                userId,
                categoryId,
                List.of("java"),
                "Search payload",
                "payload content",
                0,
                0,
                Instant.parse("2026-03-22T00:00:00Z"),
                12.5
        );
        repository.save(document);

        List<PostSearchHit> items = repository.search(new PostSearchQuery("payload", null, null, 0, 10));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).postId()).isEqualTo(postId);
        assertThat(items.get(0).userId()).isEqualTo(userId);
    }
}
