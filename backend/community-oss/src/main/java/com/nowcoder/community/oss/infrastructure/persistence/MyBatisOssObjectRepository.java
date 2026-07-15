package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisOssObjectRepository implements OssObjectRepository {

    private final OssObjectMapper mapper;

    public MyBatisOssObjectRepository(OssObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean create(OssObject object) {
        try {
            return mapper.insert(OssObjectDataObject.from(object)) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public void save(OssObject object) {
        mapper.upsert(OssObjectDataObject.from(object));
    }

    @Override
    public Optional<OssObject> findById(UUID objectId) {
        return Optional.ofNullable(mapper.selectById(objectId)).map(OssObjectDataObject::toDomain);
    }
}
