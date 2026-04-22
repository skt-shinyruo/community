package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.dto.CategoryResponse;
import com.nowcoder.community.content.entity.Category;
import com.nowcoder.community.content.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.CATEGORY_NOT_FOUND;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<Category> listCategories() {
        return categoryMapper.selectCategories();
    }

    public List<CategoryResponse> listCategoryResponses() {
        return listCategories().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    public Category getById(UUID id) {
        Category c = categoryMapper.selectCategoryById(id);
        if (c == null) {
            throw new BusinessException(CATEGORY_NOT_FOUND);
        }
        return c;
    }

    public void assertExists(UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        getById(categoryId);
    }

    private CategoryResponse toCategoryResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setPosition(category.getPosition());
        response.setPostCount(category.getPostCount());
        return response;
    }
}
