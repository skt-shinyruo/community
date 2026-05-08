package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectReferenceDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface OssObjectReferenceMapper {

    int upsert(OssObjectReferenceDataObject row);

    OssObjectReferenceDataObject selectById(UUID referenceId);

    List<OssObjectReferenceDataObject> selectByObjectId(UUID objectId);
}
