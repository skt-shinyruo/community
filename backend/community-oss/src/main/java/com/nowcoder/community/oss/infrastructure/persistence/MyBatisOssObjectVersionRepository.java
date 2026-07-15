package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectVersionDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssObjectVersionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisOssObjectVersionRepository implements OssObjectVersionRepository {

    private final OssObjectVersionMapper mapper;

    public MyBatisOssObjectVersionRepository(OssObjectVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean create(OssObjectVersion version) {
        try {
            return mapper.insert(OssObjectVersionDataObject.from(version)) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public void save(OssObjectVersion version) {
        mapper.upsert(OssObjectVersionDataObject.from(version));
    }

    @Override
    public Optional<OssObjectVersion> findById(UUID versionId) {
        return Optional.ofNullable(mapper.selectById(versionId)).map(OssObjectVersionDataObject::toDomain);
    }
}
