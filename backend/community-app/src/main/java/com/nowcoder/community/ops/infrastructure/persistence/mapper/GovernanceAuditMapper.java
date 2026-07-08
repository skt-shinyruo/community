package com.nowcoder.community.ops.infrastructure.persistence.mapper;

import com.nowcoder.community.ops.infrastructure.persistence.dataobject.GovernanceAuditDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface GovernanceAuditMapper {

    int insert(GovernanceAuditDataObject audit);

    GovernanceAuditDataObject selectById(@Param("id") UUID id);
}
