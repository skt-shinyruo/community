package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssUploadSessionDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface OssUploadSessionMapper {

    int upsert(OssUploadSessionDataObject row);

    OssUploadSessionDataObject selectById(UUID sessionId);
}
