package com.nowcoder.community.content.api;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.content.api.dto.CategoryResponse;
import com.nowcoder.community.content.entity.Category;
import com.nowcoder.community.content.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<CategoryResponse>> list() {
        List<Category> categories = categoryService.listCategories();
        List<CategoryResponse> resp = categories.stream().map(CategoryController::toResp).toList();
        return Result.ok(resp);
    }

    private static CategoryResponse toResp(Category c) {
        CategoryResponse r = new CategoryResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setDescription(c.getDescription());
        r.setPosition(c.getPosition());
        r.setPostCount(c.getPostCount());
        return r;
    }
}

