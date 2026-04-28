package com.nowcoder.community.search.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.search.application.SearchApplicationService;
import com.nowcoder.community.search.application.command.SearchPostsCommand;
import com.nowcoder.community.search.application.result.SearchPostResult;
import com.nowcoder.community.search.controller.dto.SearchPostItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        Result<List<SearchPostItemResponse>> result = controller.searchPosts("spring", categoryId, "java", 0, 10);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getPostId()).isEqualTo(item.postId());
        assertThat(result.getData().get(0).getTitle()).isEqualTo("spring");
        assertThat(result.getData().get(0).getHighlightedTitle()).isEqualTo("<em>spring</em>");
        verify(searchApplicationService).searchPosts(new SearchPostsCommand("spring", categoryId, "java", 0, 10));
    }
}
