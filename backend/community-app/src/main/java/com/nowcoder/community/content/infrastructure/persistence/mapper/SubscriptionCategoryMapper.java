// 分类订阅 MyBatis Mapper：用于查询用户订阅的分类集合。
package com.nowcoder.community.content.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SubscriptionCategoryMapper {

    List<UUID> selectSubscribedCategoryIds(@Param("userId") UUID userId);
}
