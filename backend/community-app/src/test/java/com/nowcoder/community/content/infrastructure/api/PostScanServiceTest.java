package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.application.PostContentBlockTextProjector;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import com.nowcoder.community.content.application.ContentTextCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostScanServiceTest {

    @Test
    void scanPostsShouldBuildProjectionViewsAndCursor() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        ContentTextCodec textCodec = new ContentTextCodec();
        UUID postId = uuid(10);
        UUID userId = uuid(2);
        UUID categoryId = uuid(3);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(userId);
        post.setCategoryId(categoryId);
        post.setTitle("&lt;title&gt;");
        post.setType(0);
        post.setStatus(0);
        post.setScore(1.5);

        when(discussPostMapper.selectDiscussPostsAfterId(null, 5)).thenReturn(List.of(post));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java")));
        when(blockRepository.listByPostIds(List.of(postId))).thenReturn(Map.of(
                postId,
                List.of(new PostContentBlock(uuid(51), postId, 0, "paragraph", "&lt;content&gt;", null, "", "", "", null))
        ));

        PostScanQueryApi service = new PostScanService(
                discussPostMapper,
                blockRepository,
                tagService,
                new PostContentBlockTextProjector(),
                textCodec
        );

        PostScanView response = service.scanPosts(null, 5);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).postId()).isEqualTo(postId);
        assertThat(response.items().get(0).title()).isEqualTo("&lt;title&gt;");
        assertThat(response.items().get(0).content()).isEqualTo("&lt;content&gt;");
        assertThat(response.items().get(0).tags()).containsExactly("java");
        assertThat(response.nextAfterId()).isEqualTo(postId);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void getPostProjectionAllowDeletedShouldReturnProjectionView() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        ContentTextCodec textCodec = new ContentTextCodec();
        UUID postId = uuid(11);
        UUID userId = uuid(3);
        UUID categoryId = uuid(4);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(userId);
        post.setCategoryId(categoryId);
        post.setTitle("&lt;arch&gt;");
        post.setType(1);
        post.setStatus(2);
        post.setScore(2.5);

        when(discussPostMapper.selectDiscussPostById(postId)).thenReturn(post);
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("search")));
        when(blockRepository.listByPostId(postId)).thenReturn(List.of(
                new PostContentBlock(uuid(52), postId, 0, "paragraph", "&lt;boundary&gt;", null, "", "", "", null)
        ));

        PostScanQueryApi service = new PostScanService(
                discussPostMapper,
                blockRepository,
                tagService,
                new PostContentBlockTextProjector(),
                textCodec
        );

        PostScanView.PostProjectionView projection = service.getPostProjectionAllowDeleted(postId);

        assertThat(projection).isNotNull();
        assertThat(projection.postId()).isEqualTo(postId);
        assertThat(projection.title()).isEqualTo("&lt;arch&gt;");
        assertThat(projection.content()).isEqualTo("&lt;boundary&gt;");
        assertThat(projection.tags()).containsExactly("search");
        assertThat(projection.status()).isEqualTo(2);
    }
}
