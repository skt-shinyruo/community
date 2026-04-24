package com.nowcoder.community.content.service;

import com.nowcoder.community.content.dto.CategoryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryApplicationService {

    private final CategoryService categoryService;

    public CategoryApplicationService(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public List<CategoryResponse> listCategoryResponses() {
        return categoryService.listCategoryResponses();
    }
}
