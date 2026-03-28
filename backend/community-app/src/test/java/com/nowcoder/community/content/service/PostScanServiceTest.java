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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostScanServiceTest {

    @Test
    void scanPostsShouldBuildProjectionViewsAndCursor() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        TagService tagService = mock(TagService.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        DiscussPost post = new DiscussPost();
        post.setId(10);
        post.setUserId(2);
        post.setCategoryId(3);
        post.setTitle("&lt;title&gt;");
        post.setContent("&lt;content&gt;");
        post.setType(0);
        post.setStatus(0);
        post.setScore(1.5);

        when(discussPostMapper.selectDiscussPostsAfterId(0, 5)).thenReturn(List.of(post));
        when(tagService.getTagsByPostIds(List.of(10))).thenReturn(Map.of(10, List.of("java")));

        PostScanQueryApi service = new PostScanService(discussPostMapper, tagService, textCodec);

        PostScanView response = service.scanPosts(0, 5);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).postId()).isEqualTo(10);
        assertThat(response.items().get(0).title()).isEqualTo("<title>");
        assertThat(response.items().get(0).content()).isEqualTo("<content>");
        assertThat(response.items().get(0).tags()).containsExactly("java");
        assertThat(response.nextAfterId()).isEqualTo(10);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void getPostProjectionAllowDeletedShouldReturnProjectionView() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        TagService tagService = mock(TagService.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        DiscussPost post = new DiscussPost();
        post.setId(11);
        post.setUserId(3);
        post.setCategoryId(4);
        post.setTitle("&lt;arch&gt;");
        post.setContent("&lt;boundary&gt;");
        post.setType(1);
        post.setStatus(2);
        post.setScore(2.5);

        when(discussPostMapper.selectDiscussPostById(11)).thenReturn(post);
        when(tagService.getTagsByPostIds(List.of(11))).thenReturn(Map.of(11, List.of("search")));

        PostScanQueryApi service = new PostScanService(discussPostMapper, tagService, textCodec);

        PostScanView.PostProjectionView projection = service.getPostProjectionAllowDeleted(11);

        assertThat(projection).isNotNull();
        assertThat(projection.postId()).isEqualTo(11);
        assertThat(projection.title()).isEqualTo("<arch>");
        assertThat(projection.content()).isEqualTo("<boundary>");
        assertThat(projection.tags()).containsExactly("search");
        assertThat(projection.status()).isEqualTo(2);
    }
}
