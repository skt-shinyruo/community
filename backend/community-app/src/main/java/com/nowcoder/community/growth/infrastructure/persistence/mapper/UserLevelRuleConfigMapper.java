package com.nowcoder.community.growth.infrastructure.persistence.mapper;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserLevelRuleConfigMapper {

    UserLevelRuleConfig selectCurrent();

    int updateCurrent(UserLevelRuleConfig config);

    int insert(UserLevelRuleConfig config);
}
