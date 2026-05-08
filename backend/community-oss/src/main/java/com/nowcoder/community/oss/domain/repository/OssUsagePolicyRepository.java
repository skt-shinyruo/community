package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssUsagePolicy;

import java.util.Optional;

public interface OssUsagePolicyRepository {

    void save(OssUsagePolicy policy);

    Optional<OssUsagePolicy> findByUsage(String usage);
}
