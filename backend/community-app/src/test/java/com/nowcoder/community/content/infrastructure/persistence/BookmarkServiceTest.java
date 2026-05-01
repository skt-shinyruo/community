package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.BookmarkMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookmarkServiceTest {

    @Test
    void listBookmarkedPostsShouldReadPagedRows() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        UUID userId = uuid(7);
        UUID postId = uuid(11);

        DiscussPost post = new DiscussPost();
        post.setId(postId);

        when(bookmarkMapper.selectBookmarkedPosts(userId, 0, 10)).thenReturn(List.of(post));

        MyBatisBookmarkRepository service = new MyBatisBookmarkRepository(bookmarkMapper, postContentRepository);

        List<DiscussPost> posts = service.listBookmarkedPosts(userId, 0, 10);

        assertThat(posts).containsExactly(post);
        verify(bookmarkMapper).selectBookmarkedPosts(userId, 0, 10);
    }
}
