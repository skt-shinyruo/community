package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.text.ContentTextCodec;
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
        TagService tagService = mock(TagService.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());
        UUID postId = uuid(10);
        UUID userId = uuid(2);
        UUID categoryId = uuid(3);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(userId);
        post.setCategoryId(categoryId);
        post.setTitle("&lt;title&gt;");
        post.setContent("&lt;content&gt;");
        post.setType(0);
        post.setStatus(0);
        post.setScore(1.5);

        when(discussPostMapper.selectDiscussPostsAfterId(null, 5)).thenReturn(List.of(post));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java")));

        PostScanQueryApi service = new PostScanService(discussPostMapper, tagService, textCodec);

        PostScanView response = service.scanPosts(null, 5);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).postId()).isEqualTo(postId);
        assertThat(response.items().get(0).title()).isEqualTo("<title>");
        assertThat(response.items().get(0).content()).isEqualTo("<content>");
        assertThat(response.items().get(0).tags()).containsExactly("java");
        assertThat(response.nextAfterId()).isEqualTo(postId);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void getPostProjectionAllowDeletedShouldReturnProjectionView() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        TagService tagService = mock(TagService.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());
        UUID postId = uuid(11);
        UUID userId = uuid(3);
        UUID categoryId = uuid(4);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(userId);
        post.setCategoryId(categoryId);
        post.setTitle("&lt;arch&gt;");
        post.setContent("&lt;boundary&gt;");
        post.setType(1);
        post.setStatus(2);
        post.setScore(2.5);

        when(discussPostMapper.selectDiscussPostById(postId)).thenReturn(post);
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("search")));

        PostScanQueryApi service = new PostScanService(discussPostMapper, tagService, textCodec);

        PostScanView.PostProjectionView projection = service.getPostProjectionAllowDeleted(postId);

        assertThat(projection).isNotNull();
        assertThat(projection.postId()).isEqualTo(postId);
        assertThat(projection.title()).isEqualTo("<arch>");
        assertThat(projection.content()).isEqualTo("<boundary>");
        assertThat(projection.tags()).containsExactly("search");
        assertThat(projection.status()).isEqualTo(2);
    }
}
