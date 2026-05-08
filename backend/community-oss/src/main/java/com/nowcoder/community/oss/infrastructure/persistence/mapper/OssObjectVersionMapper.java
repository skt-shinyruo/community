package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectVersionDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface OssObjectVersionMapper {

    int upsert(OssObjectVersionDataObject row);

    OssObjectVersionDataObject selectById(UUID versionId);
}
