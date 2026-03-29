package com.nowcoder.community.content.service;

import com.nowcoder.community.content.dto.CategoryResponse;
import com.nowcoder.community.content.entity.Category;
import com.nowcoder.community.content.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    @Test
    void listCategoryResponsesShouldProjectCategoryEntities() {
        CategoryMapper categoryMapper = mock(CategoryMapper.class);
        Category category = new Category();
        category.setId(1);
        category.setName("公告");
        category.setDescription("官方公告/规则");
        category.setPosition(0);
        category.setPostCount(7);
        when(categoryMapper.selectCategories()).thenReturn(List.of(category));

        CategoryService service = new CategoryService(categoryMapper);

        List<CategoryResponse> responses = service.listCategoryResponses();

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(1);
            assertThat(response.getName()).isEqualTo("公告");
            assertThat(response.getDescription()).isEqualTo("官方公告/规则");
            assertThat(response.getPosition()).isEqualTo(0);
            assertThat(response.getPostCount()).isEqualTo(7);
        });
    }
}
