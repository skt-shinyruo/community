package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.dto.HotTagResponse;
import com.nowcoder.community.content.service.TagService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TagControllerTest {

    @Test
    void hotShouldReturnServiceProjectedResponses() {
        TagService tagService = mock(TagService.class);
        HotTagResponse tag = new HotTagResponse();
        tag.setName("spring");
        tag.setUseCount(42);
        when(tagService.listHotTagResponses(8)).thenReturn(List.of(tag));

        TagController controller = new TagController(tagService);

        Result<List<HotTagResponse>> result = controller.hot(8);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(tag);
    }

    @Test
    void suggestShouldReturnServiceProjectedResponses() {
        TagService tagService = mock(TagService.class);
        HotTagResponse tag = new HotTagResponse();
        tag.setName("spring-boot");
        tag.setUseCount(15);
        when(tagService.suggestTagResponses("spring", 5)).thenReturn(List.of(tag));

        TagController controller = new TagController(tagService);

        Result<List<HotTagResponse>> result = controller.suggest("spring", 5);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(tag);
    }
}
