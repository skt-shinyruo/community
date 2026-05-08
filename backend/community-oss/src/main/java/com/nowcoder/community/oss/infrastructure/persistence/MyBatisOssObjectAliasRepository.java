package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssObjectAlias;
import com.nowcoder.community.oss.domain.repository.OssObjectAliasRepository;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectAliasDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssObjectAliasMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisOssObjectAliasRepository implements OssObjectAliasRepository {

    private final OssObjectAliasMapper mapper;

    public MyBatisOssObjectAliasRepository(OssObjectAliasMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OssObjectAlias alias) {
        mapper.upsert(OssObjectAliasDataObject.from(alias));
    }

    @Override
    public Optional<OssObjectAlias> findByAliasKey(String aliasKey) {
        return Optional.ofNullable(mapper.selectByAliasKey(aliasKey)).map(OssObjectAliasDataObject::toDomain);
    }
}
