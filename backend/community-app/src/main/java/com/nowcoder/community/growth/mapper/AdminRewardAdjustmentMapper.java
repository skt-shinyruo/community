package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.AdminRewardAdjustment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface AdminRewardAdjustmentMapper {

    int insert(AdminRewardAdjustment adjustment);

    List<AdminRewardAdjustment> selectRecentByTargetUserId(@Param("targetUserId") int targetUserId, @Param("limit") int limit);
}
