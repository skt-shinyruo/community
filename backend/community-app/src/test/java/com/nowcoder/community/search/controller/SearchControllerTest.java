package com.nowcoder.community.search.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.search.dto.SearchPostItem;
import com.nowcoder.community.search.service.SearchApplicationService;
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
        SearchPostItem item = new SearchPostItem();
        item.setPostId(uuid(11));
        item.setTitle("spring");
        when(searchApplicationService.searchPosts("spring", categoryId, "java", 0, 10))
                .thenReturn(List.of(item));

        Result<List<SearchPostItem>> result = controller.searchPosts("spring", categoryId, "java", 0, 10);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(item);
        verify(searchApplicationService).searchPosts("spring", categoryId, "java", 0, 10);
    }
}
