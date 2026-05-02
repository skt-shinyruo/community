package com.nowcoder.community.social.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLikeRepositoryTest {

    @Test
    void incrementUserLikeCountShouldClampMissingAndExistingRowsAtZero() {
        InMemoryLikeRepository repository = new InMemoryLikeRepository();

        assertThat(repository.incrementUserLikeCount(uuid(1), -1)).isZero();
        assertThat(repository.getUserLikeCount(uuid(1))).isZero();

        assertThat(repository.incrementUserLikeCount(uuid(1), 2)).isEqualTo(2);
        assertThat(repository.incrementUserLikeCount(uuid(1), -3)).isZero();
        assertThat(repository.getUserLikeCount(uuid(1))).isZero();
    }
}
