package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.repository.OssObjectReferenceRepository;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectReferenceDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssObjectReferenceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisOssObjectReferenceRepository implements OssObjectReferenceRepository {

    private final OssObjectReferenceMapper mapper;

    public MyBatisOssObjectReferenceRepository(OssObjectReferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OssObjectReference reference) {
        OssObjectReferenceDataObject row = OssObjectReferenceDataObject.from(reference);
        if (mapper.updateLifecycle(row) == 0) {
            mapper.insert(row);
        }
    }

    @Override
    public OssObjectReference insertOrFindExisting(OssObjectReference reference) {
        try {
            mapper.insert(OssObjectReferenceDataObject.from(reference));
            return reference;
        } catch (DuplicateKeyException ignored) {
            OssObjectReferenceDataObject existing = mapper.selectByIdForUpdate(reference.referenceId());
            if (existing == null) {
                throw new IllegalStateException("object reference insert lost without an existing row");
            }
            return existing.toDomain();
        }
    }

    @Override
    public Optional<OssObjectReference> findById(UUID referenceId) {
        return Optional.ofNullable(mapper.selectById(referenceId)).map(OssObjectReferenceDataObject::toDomain);
    }

    @Override
    public List<OssObjectReference> findByObjectId(UUID objectId) {
        return mapper.selectByObjectId(objectId).stream().map(OssObjectReferenceDataObject::toDomain).toList();
    }
}
