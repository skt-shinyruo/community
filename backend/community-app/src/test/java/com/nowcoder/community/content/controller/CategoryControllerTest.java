package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.CategoryApplicationService.CategoryResult;
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

        Result<List<CategoryResult>> result = controller.list();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly(category);
    }
}
