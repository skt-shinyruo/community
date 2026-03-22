package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.dto.CategoryResponse;
import com.nowcoder.community.content.entity.Category;
import com.nowcoder.community.content.service.CategoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryControllerTest {

    @Test
    void listShouldKeepCategoryFieldsAsReturnedByService() {
        CategoryService categoryService = mock(CategoryService.class);

        Category category = new Category();
        category.setId(1);
        category.setName("公告");
        category.setDescription("官方公告/规则");
        category.setPosition(0);
        category.setPostCount(0);

        when(categoryService.listCategories()).thenReturn(List.of(category));

        CategoryController controller = new CategoryController(categoryService);

        Result<List<CategoryResponse>> result = controller.list();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getName()).isEqualTo("公告");
        assertThat(result.getData().get(0).getDescription()).isEqualTo("官方公告/规则");
    }
}
