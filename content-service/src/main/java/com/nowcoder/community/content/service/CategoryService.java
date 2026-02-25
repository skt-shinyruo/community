package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.content.dao.CategoryMapper;
import com.nowcoder.community.content.entity.Category;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.content.api.ContentErrorCode.CATEGORY_NOT_FOUND;
import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<Category> listCategories() {
        return categoryMapper.selectCategories();
    }

    public Category getById(int id) {
        Category c = categoryMapper.selectCategoryById(id);
        if (c == null) {
            throw new BusinessException(CATEGORY_NOT_FOUND);
        }
        return c;
    }

    public void assertExists(Integer categoryId) {
        if (categoryId == null) {
            return;
        }
        if (categoryId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "分类参数非法");
        }
        getById(categoryId);
    }
}
