package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.result.CategoryResult;
import com.nowcoder.community.content.controller.dto.CategoryResponse;
import com.nowcoder.community.content.application.CategoryApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryApplicationService categoryApplicationService;

    public CategoryController(CategoryApplicationService categoryApplicationService) {
        this.categoryApplicationService = categoryApplicationService;
    }

    @GetMapping
    public Result<List<CategoryResponse>> list() {
        return Result.ok(categoryApplicationService.listCategories().stream()
                .map(this::toCategoryResponse)
                .toList());
    }

    private CategoryResponse toCategoryResponse(CategoryResult category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.id());
        response.setName(category.name());
        response.setDescription(category.description());
        response.setPosition(category.position());
        response.setPostCount(category.postCount());
        return response;
    }
}
