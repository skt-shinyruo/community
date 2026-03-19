// 订阅服务：MVP 先支持订阅分类（category），用于“仅看订阅”筛选。
package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.mapper.SubscriptionCategoryMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class SubscriptionService {

    private final SubscriptionCategoryMapper categoryMapper;
    private final CategoryService categoryService;

    public SubscriptionService(SubscriptionCategoryMapper categoryMapper, CategoryService categoryService) {
        this.categoryMapper = categoryMapper;
        this.categoryService = categoryService;
    }

    public void subscribeCategory(int userId, int categoryId) {
        if (userId <= 0 || categoryId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/categoryId 非法");
        }
        categoryService.getById(categoryId);
        categoryMapper.insertSubscription(userId, categoryId, new Date());
    }

    public void unsubscribeCategory(int userId, int categoryId) {
        if (userId <= 0 || categoryId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/categoryId 非法");
        }
        categoryMapper.deleteSubscription(userId, categoryId);
    }

    public boolean hasSubscribedCategory(int userId, int categoryId) {
        if (userId <= 0 || categoryId <= 0) {
            return false;
        }
        return categoryMapper.existsSubscription(userId, categoryId) > 0;
    }

    public List<Integer> listSubscribedCategoryIds(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return categoryMapper.selectSubscribedCategoryIds(userId);
    }
}

