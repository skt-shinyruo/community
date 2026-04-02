package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.UserLevelRuleConfig;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserLevelRuleConfigMapper {

    UserLevelRuleConfig selectCurrent();
}
