package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface OssObjectMapper {

    int upsert(OssObjectDataObject row);

    OssObjectDataObject selectById(UUID objectId);
}
