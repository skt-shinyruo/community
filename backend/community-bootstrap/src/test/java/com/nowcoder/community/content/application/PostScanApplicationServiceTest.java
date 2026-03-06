package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.dto.PostScanResult;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.dao.DiscussPostMapper;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostScanApplicationServiceTest {

    @Test
    void scanPostsShouldBuildPayloadsAndCursor() {
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

        PostScanApplicationService service = new PostScanApplicationService(discussPostMapper, tagService, textCodec);

        PostScanResult response = service.scanPosts(0, 5);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getPostId()).isEqualTo(10);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("<title>");
        assertThat(response.getItems().get(0).getContent()).isEqualTo("<content>");
        assertThat(response.getItems().get(0).getTags()).containsExactly("java");
        assertThat(response.getNextAfterId()).isEqualTo(10);
        assertThat(response.isHasMore()).isFalse();
    }
}
