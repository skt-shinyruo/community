package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssUsagePolicyDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface OssUsagePolicyMapper {

    int upsert(OssUsagePolicyDataObject row);

    OssUsagePolicyDataObject selectByUsage(String usage);
}
