package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.RewardAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface RewardAccountMapper {

    RewardAccount selectByUserId(UUID userId);

    int insertAccount(UUID userId);

    int addAvailableBalance(@Param("userId") UUID userId, @Param("delta") int delta);

    int moveAvailableToFrozen(@Param("userId") UUID userId, @Param("amount") int amount);

    int moveFrozenToAvailable(@Param("userId") UUID userId, @Param("amount") int amount);

    int deductFrozenBalance(@Param("userId") UUID userId, @Param("amount") int amount);
}
