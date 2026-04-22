// 分类订阅 MyBatis Mapper：用于查询用户订阅的分类集合，并支持订阅/取消订阅。
package com.nowcoder.community.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SubscriptionCategoryMapper {

    int insertSubscription(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId, @Param("createTime") Date createTime);

    int deleteSubscription(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId);

    int existsSubscription(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId);

    List<UUID> selectSubscribedCategoryIds(@Param("userId") UUID userId);
}
