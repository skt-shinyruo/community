package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisPostContentRepositoryTest {

    @Test
    void listRecentVisiblePostsByAuthorIdsShouldForwardCallerLimitAboveFiveHundred() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        MyBatisPostContentRepository repository = new MyBatisPostContentRepository(discussPostMapper);
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();

        when(discussPostMapper.selectRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 641))
                .thenReturn(List.of());

        repository.listRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 641);

        verify(discussPostMapper).selectRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 641);
    }

    @Test
    void listRecentVisiblePostsByAuthorIdsBeforeShouldForwardAnchorAndCallerLimit() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        MyBatisPostContentRepository repository = new MyBatisPostContentRepository(discussPostMapper);
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        UUID anchorPostId = UUID.randomUUID();
        Date anchorCreateTime = new Date(2_000L);

        when(discussPostMapper.selectRecentVisiblePostsByAuthorIdsBefore(
                List.of(authorA, authorB),
                anchorCreateTime,
                anchorPostId,
                21
        )).thenReturn(List.of());

        repository.listRecentVisiblePostsByAuthorIdsBefore(List.of(authorA, authorB), anchorCreateTime, anchorPostId, 21);

        verify(discussPostMapper).selectRecentVisiblePostsByAuthorIdsBefore(
                List.of(authorA, authorB),
                anchorCreateTime,
                anchorPostId,
                21
        );
    }
}
