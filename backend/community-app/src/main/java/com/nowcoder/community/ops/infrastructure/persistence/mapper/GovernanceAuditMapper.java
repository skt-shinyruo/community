package com.nowcoder.community.ops.infrastructure.persistence.mapper;

import com.nowcoder.community.ops.infrastructure.persistence.dataobject.GovernanceAuditDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface GovernanceAuditMapper {

    int insert(GovernanceAuditDataObject audit);
}
