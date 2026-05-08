package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssAccessGrantDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface OssAccessGrantMapper {

    int upsert(OssAccessGrantDataObject row);

    OssAccessGrantDataObject selectById(UUID grantId);

    List<OssAccessGrantDataObject> selectByObjectId(UUID objectId);
}
