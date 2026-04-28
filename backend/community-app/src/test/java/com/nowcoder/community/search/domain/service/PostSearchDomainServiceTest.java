package com.nowcoder.community.search.domain.service;

import com.nowcoder.community.search.domain.model.PostSearchQuery;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class PostSearchDomainServiceTest {

    private final PostSearchDomainService service = new PostSearchDomainService();

    @Test
    void normalizeSearchQueryShouldClampPagingAndTag() {
        UUID categoryId = uuid(3);

        PostSearchQuery query = service.normalizeSearchQuery(" spring ", categoryId, "#java", -10, 200);

        assertThat(query.keyword()).isEqualTo("spring");
        assertThat(query.categoryId()).isEqualTo(categoryId);
        assertThat(query.tag()).isEqualTo("java");
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(50);
    }

    @Test
    void shouldIndexShouldRejectDeletedOrMissingPost() {
        assertThat(service.shouldIndex(uuid(11), 0)).isTrue();
        assertThat(service.shouldIndex(uuid(11), null)).isTrue();
        assertThat(service.shouldIndex(uuid(11), 2)).isFalse();
        assertThat(service.shouldIndex(null, 0)).isFalse();
    }
}
