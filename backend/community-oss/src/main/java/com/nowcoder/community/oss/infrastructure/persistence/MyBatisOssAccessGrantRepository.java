package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssAccessGrantDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssAccessGrantMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisOssAccessGrantRepository implements OssAccessGrantRepository {

    private final OssAccessGrantMapper mapper;

    public MyBatisOssAccessGrantRepository(OssAccessGrantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OssAccessGrant grant) {
        mapper.upsert(OssAccessGrantDataObject.from(grant));
    }

    @Override
    public Optional<OssAccessGrant> findById(UUID grantId) {
        return Optional.ofNullable(mapper.selectById(grantId)).map(OssAccessGrantDataObject::toDomain);
    }

    @Override
    public List<OssAccessGrant> findByObjectId(UUID objectId) {
        return mapper.selectByObjectId(objectId).stream().map(OssAccessGrantDataObject::toDomain).toList();
    }
}
