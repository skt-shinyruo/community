package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.port.CategoryContentPort;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.CATEGORY_NOT_FOUND;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class CategoryService implements CategoryContentPort {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<Category> listCategories() {
        return categoryMapper.selectCategories();
    }

    @Override
    public Category getById(UUID id) {
        Category c = categoryMapper.selectCategoryById(id);
        if (c == null) {
            throw new BusinessException(CATEGORY_NOT_FOUND);
        }
        return c;
    }

    @Override
    public void assertExists(UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        getById(categoryId);
    }

}
