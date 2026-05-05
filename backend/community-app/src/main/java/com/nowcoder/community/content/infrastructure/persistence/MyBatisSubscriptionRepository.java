// 订阅服务：查询用户订阅的分类列表。
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.SubscriptionRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.SubscriptionCategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class MyBatisSubscriptionRepository implements SubscriptionRepository {

    private final SubscriptionCategoryMapper categoryMapper;

    public MyBatisSubscriptionRepository(SubscriptionCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<UUID> listSubscribedCategoryIds(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return categoryMapper.selectSubscribedCategoryIds(userId);
    }
}
