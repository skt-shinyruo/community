package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.CategoryRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CategoryMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.CATEGORY_NOT_FOUND;

@Repository
public class MyBatisCategoryRepository implements CategoryRepository {

    private final CategoryMapper categoryMapper;

    public MyBatisCategoryRepository(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public void assertExists(UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        if (categoryMapper.selectCategoryById(categoryId) == null) {
            throw new BusinessException(CATEGORY_NOT_FOUND);
        }
    }
}
