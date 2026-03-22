package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.RewardOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface RewardOrderMapper {

    RewardOrder selectById(long id);

    RewardOrder selectByIdForUpdate(long id);

    RewardOrder selectByUserIdAndRedeemRequestId(@Param("userId") int userId, @Param("redeemRequestId") String redeemRequestId);

    RewardOrder selectByUserIdAndRedeemRequestIdForUpdate(@Param("userId") int userId, @Param("redeemRequestId") String redeemRequestId);

    java.util.List<RewardOrder> selectByUserId(int userId);

    java.util.List<RewardOrder> selectAll();

    int countActiveUserOrdersForItem(@Param("userId") int userId, @Param("itemId") long itemId);

    int countByStatus(String status);

    int insert(RewardOrder rewardOrder);

    int updateStatus(@Param("id") long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
}
