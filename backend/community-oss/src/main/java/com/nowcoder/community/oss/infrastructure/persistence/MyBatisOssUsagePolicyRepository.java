package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssUsagePolicy;
import com.nowcoder.community.oss.domain.repository.OssUsagePolicyRepository;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssUsagePolicyDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssUsagePolicyMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisOssUsagePolicyRepository implements OssUsagePolicyRepository {

    private final OssUsagePolicyMapper mapper;

    public MyBatisOssUsagePolicyRepository(OssUsagePolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OssUsagePolicy policy) {
        mapper.upsert(OssUsagePolicyDataObject.from(policy));
    }

    @Override
    public Optional<OssUsagePolicy> findByUsage(String usage) {
        return Optional.ofNullable(mapper.selectByUsage(usage)).map(OssUsagePolicyDataObject::toDomain);
    }
}
