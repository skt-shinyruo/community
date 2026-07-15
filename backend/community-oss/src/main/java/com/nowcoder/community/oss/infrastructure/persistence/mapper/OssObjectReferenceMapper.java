package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectReferenceDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface OssObjectReferenceMapper {

    int insert(OssObjectReferenceDataObject row);

    int updateLifecycle(OssObjectReferenceDataObject row);

    OssObjectReferenceDataObject selectById(UUID referenceId);

    OssObjectReferenceDataObject selectByIdForUpdate(UUID referenceId);

    List<OssObjectReferenceDataObject> selectByObjectId(UUID objectId);
}
