package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    @Test
    void listCategoriesShouldReturnCategoryEntities() {
        CategoryMapper categoryMapper = mock(CategoryMapper.class);
        Category category = new Category();
        category.setId(uuid(1));
        category.setName("公告");
        category.setDescription("官方公告/规则");
        category.setPosition(0);
        category.setPostCount(7);
        when(categoryMapper.selectCategories()).thenReturn(List.of(category));

        MyBatisCategoryContentRepository service = new MyBatisCategoryContentRepository(categoryMapper);

        List<Category> responses = service.listCategories();

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(uuid(1));
            assertThat(response.getName()).isEqualTo("公告");
            assertThat(response.getDescription()).isEqualTo("官方公告/规则");
            assertThat(response.getPosition()).isEqualTo(0);
            assertThat(response.getPostCount()).isEqualTo(7);
        });
    }
}
