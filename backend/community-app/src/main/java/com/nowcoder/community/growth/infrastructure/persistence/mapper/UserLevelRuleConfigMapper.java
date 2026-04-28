package com.nowcoder.community.growth.infrastructure.persistence.mapper;

import com.nowcoder.community.growth.infrastructure.persistence.dataobject.UserLevelRuleConfigDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserLevelRuleConfigMapper {

    UserLevelRuleConfigDataObject selectCurrent();

    int updateCurrent(UserLevelRuleConfigDataObject config);

    int insert(UserLevelRuleConfigDataObject config);
}
