package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssUploadSessionDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssUploadSessionMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisOssUploadSessionRepository implements OssUploadSessionRepository {

    private final OssUploadSessionMapper mapper;

    public MyBatisOssUploadSessionRepository(OssUploadSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OssUploadSession session) {
        mapper.upsert(OssUploadSessionDataObject.from(session));
    }

    @Override
    public Optional<OssUploadSession> findById(UUID sessionId) {
        return Optional.ofNullable(mapper.selectById(sessionId)).map(OssUploadSessionDataObject::toDomain);
    }
}
