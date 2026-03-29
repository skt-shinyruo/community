package com.nowcoder.community.content.service;

import com.nowcoder.community.content.dto.HotTagResponse;
import com.nowcoder.community.content.entity.HotTag;
import com.nowcoder.community.content.mapper.PostTagMapper;
import com.nowcoder.community.content.mapper.TagMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagServiceTest {

    @Test
    void listHotTagResponsesShouldProjectHotTags() {
        TagMapper tagMapper = mock(TagMapper.class);
        PostTagMapper postTagMapper = mock(PostTagMapper.class);
        HotTag hotTag = new HotTag();
        hotTag.setName("spring");
        hotTag.setUseCount(42);
        when(tagMapper.selectHotTags(8)).thenReturn(List.of(hotTag));

        TagService service = new TagService(tagMapper, postTagMapper);

        List<HotTagResponse> responses = service.listHotTagResponses(null);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getName()).isEqualTo("spring");
            assertThat(response.getUseCount()).isEqualTo(42);
        });
        verify(tagMapper).selectHotTags(8);
    }

    @Test
    void suggestTagResponsesShouldProjectSuggestedTags() {
        TagMapper tagMapper = mock(TagMapper.class);
        PostTagMapper postTagMapper = mock(PostTagMapper.class);
        HotTag hotTag = new HotTag();
        hotTag.setName("spring-boot");
        hotTag.setUseCount(15);
        when(tagMapper.selectSuggestTags("spring", 5)).thenReturn(List.of(hotTag));

        TagService service = new TagService(tagMapper, postTagMapper);

        List<HotTagResponse> responses = service.suggestTagResponses("spring", 5);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getName()).isEqualTo("spring-boot");
            assertThat(response.getUseCount()).isEqualTo(15);
        });
    }
}
