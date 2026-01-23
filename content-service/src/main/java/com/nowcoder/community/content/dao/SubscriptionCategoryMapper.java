// 分类订阅 MyBatis Mapper：用于查询用户订阅的分类集合，并支持订阅/取消订阅。
package com.nowcoder.community.content.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface SubscriptionCategoryMapper {

    int insertSubscription(@Param("userId") int userId, @Param("categoryId") int categoryId, @Param("createTime") Date createTime);

    int deleteSubscription(@Param("userId") int userId, @Param("categoryId") int categoryId);

    int existsSubscription(@Param("userId") int userId, @Param("categoryId") int categoryId);

    List<Integer> selectSubscribedCategoryIds(@Param("userId") int userId);
}

