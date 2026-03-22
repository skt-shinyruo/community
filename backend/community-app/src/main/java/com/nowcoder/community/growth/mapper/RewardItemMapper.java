package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.RewardItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface RewardItemMapper {

    java.util.List<RewardItem> selectActiveOrdered();

    java.util.List<RewardItem> selectAllOrdered();

    RewardItem selectById(long id);

    RewardItem selectByIdForUpdate(long id);

    int insert(RewardItem rewardItem);

    int update(RewardItem rewardItem);

    int countActiveItems();

    int decrementStockIfAvailable(@Param("id") long id);

    int reserveStockForRedemption(@Param("id") long id, @Param("userId") int userId);
}
