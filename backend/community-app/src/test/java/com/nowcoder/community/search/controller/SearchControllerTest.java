package com.nowcoder.community.search.controller;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.search.application.SearchApplicationService;
import com.nowcoder.community.search.application.SearchApplicationService.SearchPostResult;
import com.nowcoder.community.search.application.SearchApplicationService.SearchPostsCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchApplicationService searchApplicationService;

    private SearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SearchController(searchApplicationService);
    }

    @Test
    void searchPostsShouldDelegateToSearchApplicationService() {
        UUID categoryId = uuid(3);
        SearchPostResult item = new SearchPostResult(
                uuid(11),
                uuid(7),
                categoryId,
                List.of("java"),
                "spring",
                "<em>spring</em>",
                null,
                null,
                10.0
        );
        when(searchApplicationService.searchPosts(new SearchPostsCommand("spring", categoryId, "java", 0, 10)))
                .thenReturn(List.of(item));

        Result<List<SearchPostResult>> result = controller.searchPosts("spring", categoryId, "java", 0, 10);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).postId()).isEqualTo(item.postId());
        assertThat(result.getData().get(0).title()).isEqualTo("spring");
        assertThat(result.getData().get(0).highlightedTitle()).isEqualTo("<em>spring</em>");
        verify(searchApplicationService).searchPosts(new SearchPostsCommand("spring", categoryId, "java", 0, 10));
    }

    @Test
    void searchPostJsonShouldPreserveHighlightAndTagFields() {
        SearchPostResult item = new SearchPostResult(
                uuid(11),
                uuid(7),
                uuid(3),
                List.of("java"),
                "spring",
                "<em>spring</em>",
                "<em>spring</em> content",
                null,
                10.0
        );
        when(searchApplicationService.searchPosts(new SearchPostsCommand(null, null, null, null, null)))
                .thenReturn(List.of(item));

        JsonNode json = JacksonJsonCodec.standardMapper().valueToTree(
                controller.searchPosts(null, null, null, null, null).getData().get(0)
        );
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);

        assertThat(fields).containsExactlyInAnyOrder(
                "postId",
                "userId",
                "categoryId",
                "tags",
                "title",
                "highlightedTitle",
                "highlightedContent",
                "createTime",
                "score"
        );
        assertThat(json.path("tags").get(0).asText()).isEqualTo("java");
        assertThat(json.path("highlightedTitle").asText()).isEqualTo("<em>spring</em>");
        assertThat(json.path("highlightedContent").asText()).isEqualTo("<em>spring</em> content");
    }
}
