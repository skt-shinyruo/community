package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Component
public class PostMediaReferenceTransactionOperations {

    private final PostMediaAssetRepository assetRepository;

    public PostMediaReferenceTransactionOperations(PostMediaAssetRepository assetRepository) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PostMediaAsset load(UUID assetId) {
        return assetRepository.getRequired(assetId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markBound(UUID assetId, long operationVersion, Date updateTime) {
        return assetRepository.markBound(assetId, operationVersion, updateTime);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markReleased(UUID assetId, long operationVersion, Date updateTime) {
        return assetRepository.markReleased(assetId, operationVersion, updateTime);
    }
}
