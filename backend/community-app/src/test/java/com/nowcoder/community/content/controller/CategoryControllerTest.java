package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.result.CategoryResult;
import com.nowcoder.community.content.controller.dto.CategoryResponse;
import com.nowcoder.community.content.application.CategoryApplicationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryControllerTest {

    @Test
    void listShouldKeepCategoryFieldsAsReturnedByService() {
        CategoryApplicationService categoryApplicationService = mock(CategoryApplicationService.class);
        CategoryResult category = new CategoryResult(uuid(1), "公告", "官方公告/规则", 0, 0);

        when(categoryApplicationService.listCategories()).thenReturn(List.of(category));

        CategoryController controller = new CategoryController(categoryApplicationService);

        Result<List<CategoryResponse>> result = controller.list();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(category.id());
            assertThat(response.getName()).isEqualTo(category.name());
            assertThat(response.getDescription()).isEqualTo(category.description());
            assertThat(response.getPosition()).isEqualTo(category.position());
            assertThat(response.getPostCount()).isEqualTo(category.postCount());
        });
    }
}
