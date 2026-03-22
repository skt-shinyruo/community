package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.AdminRewardOrderAction;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface AdminRewardOrderActionMapper {

    int insert(AdminRewardOrderAction action);
}
