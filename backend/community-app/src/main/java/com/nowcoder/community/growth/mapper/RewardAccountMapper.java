package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.RewardAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface RewardAccountMapper {

    RewardAccount selectByUserId(int userId);

    int insertAccount(int userId);

    int addAvailableBalance(@Param("userId") int userId, @Param("delta") int delta);

    int moveAvailableToFrozen(@Param("userId") int userId, @Param("amount") int amount);

    int moveFrozenToAvailable(@Param("userId") int userId, @Param("amount") int amount);

    int deductFrozenBalance(@Param("userId") int userId, @Param("amount") int amount);
}
