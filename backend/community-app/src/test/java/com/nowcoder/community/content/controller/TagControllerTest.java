package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.result.HotTagResult;
import com.nowcoder.community.content.controller.dto.HotTagResponse;
import com.nowcoder.community.content.application.TagApplicationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TagControllerTest {

    @Test
    void hotShouldReturnServiceProjectedResponses() {
        TagApplicationService tagApplicationService = mock(TagApplicationService.class);
        HotTagResult tag = new HotTagResult("spring", 42);
        when(tagApplicationService.listHotTags(8)).thenReturn(List.of(tag));

        TagController controller = new TagController(tagApplicationService);

        Result<List<HotTagResponse>> result = controller.hot(8);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).singleElement().satisfies(response -> {
            assertThat(response.getName()).isEqualTo(tag.name());
            assertThat(response.getUseCount()).isEqualTo(tag.useCount());
        });
    }

    @Test
    void suggestShouldReturnServiceProjectedResponses() {
        TagApplicationService tagApplicationService = mock(TagApplicationService.class);
        HotTagResult tag = new HotTagResult("spring-boot", 15);
        when(tagApplicationService.suggestTags("spring", 5)).thenReturn(List.of(tag));

        TagController controller = new TagController(tagApplicationService);

        Result<List<HotTagResponse>> result = controller.suggest("spring", 5);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).singleElement().satisfies(response -> {
            assertThat(response.getName()).isEqualTo(tag.name());
            assertThat(response.getUseCount()).isEqualTo(tag.useCount());
        });
    }
}
