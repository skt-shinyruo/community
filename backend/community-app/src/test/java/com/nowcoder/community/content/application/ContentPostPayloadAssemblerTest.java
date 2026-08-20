package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentPostPayloadAssemblerTest {

    @Test
    void assembleShouldLoadPostTagsAndDecodeStoredText() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        TagContentRepository tagRepository = mock(TagContentRepository.class);
        ContentPostPayloadAssembler assembler =
                new ContentPostPayloadAssembler(
                        postRepository,
                        blockRepository,
                        tagRepository,
                        new PostContentBlockTextProjector(),
                        new SpringHtmlContentTextCodec()
                );

        DiscussPost post = new DiscussPost();
        post.setId(uuid(11));
        post.setUserId(uuid(7));
        post.setCategoryId(uuid(3));
        post.setTitle("&lt;title&gt;");
        post.setType(0);
        post.setStatus(0);
        post.setCreateTime(Date.from(Instant.parse("2026-04-29T09:30:00Z")));
        post.setScore(2.5);
        post.setScoreVersion(4L);
        post.setAggregateVersion(7L);

        when(postRepository.getByIdAllowDeleted(uuid(11))).thenReturn(post);
        when(tagRepository.getTagsByPostIds(List.of(uuid(11)))).thenReturn(Map.of(uuid(11), List.of("java", "ddd")));
        when(blockRepository.listByPostId(uuid(11))).thenReturn(List.of(
                new PostContentBlock(uuid(51), uuid(11), 0, "paragraph", "&lt;p&gt;body&lt;/p&gt;", null, "", "", "", null)
        ));

        PostPayload payload = assembler.assemble(uuid(11));

        assertThat(payload.postId()).isEqualTo(uuid(11));
        assertThat(payload.userId()).isEqualTo(uuid(7));
        assertThat(payload.categoryId()).isEqualTo(uuid(3));
        assertThat(payload.tags()).containsExactly("java", "ddd");
        assertThat(payload.title()).isEqualTo("<title>");
        assertThat(payload.content()).isEqualTo("<p>body</p>");
        assertThat(payload.type()).isEqualTo(0);
        assertThat(payload.status()).isEqualTo(0);
        assertThat(payload.createTime()).isEqualTo(Instant.parse("2026-04-29T09:30:00Z"));
        assertThat(payload.score()).isEqualTo(2.5);
        assertThat(payload.scoreVersion()).isEqualTo(4L);
        assertThat(payload.aggregateVersion()).isEqualTo(7L);
    }
}
