package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssObjectAlias;

import java.util.Optional;

public interface OssObjectAliasRepository {

    void save(OssObjectAlias alias);

    Optional<OssObjectAlias> findByAliasKey(String aliasKey);
}
