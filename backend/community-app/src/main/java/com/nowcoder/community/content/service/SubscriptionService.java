// 订阅服务：MVP 先支持订阅分类（category），用于“仅看订阅”筛选。
package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.mapper.SubscriptionCategoryMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class SubscriptionService {

    private final SubscriptionCategoryMapper categoryMapper;
    private final CategoryService categoryService;

    public SubscriptionService(SubscriptionCategoryMapper categoryMapper, CategoryService categoryService) {
        this.categoryMapper = categoryMapper;
        this.categoryService = categoryService;
    }

    public void subscribeCategory(UUID userId, UUID categoryId) {
        if (userId == null || categoryId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/categoryId 非法");
        }
        categoryService.getById(categoryId);
        categoryMapper.insertSubscription(userId, categoryId, new Date());
    }

    public void unsubscribeCategory(UUID userId, UUID categoryId) {
        if (userId == null || categoryId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/categoryId 非法");
        }
        categoryMapper.deleteSubscription(userId, categoryId);
    }

    public boolean hasSubscribedCategory(UUID userId, UUID categoryId) {
        if (userId == null || categoryId == null) {
            return false;
        }
        return categoryMapper.existsSubscription(userId, categoryId) > 0;
    }

    public List<UUID> listSubscribedCategoryIds(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return categoryMapper.selectSubscribedCategoryIds(userId);
    }
}
