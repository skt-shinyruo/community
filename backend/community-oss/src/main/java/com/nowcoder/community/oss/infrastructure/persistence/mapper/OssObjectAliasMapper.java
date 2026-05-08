package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectAliasDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface OssObjectAliasMapper {

    int upsert(OssObjectAliasDataObject row);

    OssObjectAliasDataObject selectByAliasKey(String aliasKey);
}
