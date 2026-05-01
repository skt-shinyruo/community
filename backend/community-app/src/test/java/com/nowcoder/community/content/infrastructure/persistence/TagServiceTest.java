package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.model.HotTag;
import com.nowcoder.community.content.infrastructure.persistence.mapper.PostTagMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.TagMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagServiceTest {

    @Test
    void listHotTagsShouldReturnHotTagModels() {
        TagMapper tagMapper = mock(TagMapper.class);
        PostTagMapper postTagMapper = mock(PostTagMapper.class);
        HotTag hotTag = new HotTag();
        hotTag.setName("spring");
        hotTag.setUseCount(42);
        when(tagMapper.selectHotTags(8)).thenReturn(List.of(hotTag));

        MyBatisTagContentRepository service = new MyBatisTagContentRepository(tagMapper, postTagMapper);

        List<HotTag> responses = service.listHotTags(null);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getName()).isEqualTo("spring");
            assertThat(response.getUseCount()).isEqualTo(42);
        });
        verify(tagMapper).selectHotTags(8);
    }

    @Test
    void suggestTagsShouldReturnSuggestedTagModels() {
        TagMapper tagMapper = mock(TagMapper.class);
        PostTagMapper postTagMapper = mock(PostTagMapper.class);
        HotTag hotTag = new HotTag();
        hotTag.setName("spring-boot");
        hotTag.setUseCount(15);
        when(tagMapper.selectSuggestTags("spring", 5)).thenReturn(List.of(hotTag));

        MyBatisTagContentRepository service = new MyBatisTagContentRepository(tagMapper, postTagMapper);

        List<HotTag> responses = service.suggestTags("spring", 5);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getName()).isEqualTo("spring-boot");
            assertThat(response.getUseCount()).isEqualTo(15);
        });
    }
}
